"""Test suite for DANASEA mock transaction datasets.

Validates referential integrity, financial calculations, state transitions,
and backend contract compliance without database connection.
"""
from pathlib import Path
from decimal import Decimal, ROUND_HALF_UP
from datetime import datetime, timedelta
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE_DIR = ROOT / 'base' if (ROOT / 'base').is_dir() else ROOT / 'data'
TRANS_DIR = ROOT / 'transactions' if (ROOT / 'transactions').is_dir() else ROOT / 'data'


class TransactionDatasetContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads((TRANS_DIR / 'demo_transactions.json').read_text(encoding='utf8'))
        cls.users = {r['sqlRow']['id']: r['sqlRow'] for r in json.loads((BASE_DIR / 'users.json').read_text(encoding='utf8'))['records']}
        cls.vendors = {r['sqlRow']['id']: r['sqlRow'] for r in json.loads((BASE_DIR / 'vendors.json').read_text(encoding='utf8'))['records']}
        cls.services = {r['sqlRow']['id']: r['sqlRow'] for r in json.loads((BASE_DIR / 'services.json').read_text(encoding='utf8'))['records']}
        cls.slots = {r['sqlRow']['id']: r['sqlRow'] for r in json.loads((BASE_DIR / 'service_slots.json').read_text(encoding='utf8'))['records']}
        cls.options = {r['sqlRow']['id']: r['sqlRow'] for r in json.loads((BASE_DIR / 'service_options.json').read_text(encoding='utf8'))['records']}

    def test_metadata_and_counts(self):
        meta = self.data['metadata']
        self.assertEqual('43', meta['targetFlyway'])
        self.assertEqual('0.1000', meta['platformCommissionRate'])
        self.assertEqual(228, meta['baseRecordsReferenced'])
        self.assertEqual(6, len(self.data['bookings']))
        self.assertEqual(6, len(self.data['booking_items']))
        self.assertEqual(6, len(self.data['master_orders']))
        self.assertEqual(6, len(self.data['sub_orders']))
        self.assertEqual(6, len(self.data['payments']))
        self.assertEqual(2, len(self.data['refunds']))
        self.assertEqual(3, len(self.data['invoices']))
        self.assertEqual(2, len(self.data['settlements']))
        self.assertEqual(3, len(self.data['settlement_line_items']))
        self.assertEqual(2, len(self.data['payout_requests']))

    def test_referential_integrity_to_base_package(self):
        # Customer checks
        for b in self.data['bookings']:
            self.assertIn(b['customer_id'], self.users, 'Booking customer must exist in users')
            self.assertEqual('CUSTOMER', self.users[b['customer_id']]['role'])

        # Service / vendor / slot checks in booking items
        for bi in self.data['booking_items']:
            self.assertIn(bi['service_id'], self.services, 'Booking item service must exist')
            self.assertIn(bi['vendor_id'], self.vendors, 'Booking item vendor must exist')
            self.assertIn(bi['slot_id'], self.slots, 'Booking item slot must exist')
            if bi['option_id']:
                self.assertIn(bi['option_id'], self.options, 'Booking item option must exist')
                self.assertEqual(bi['service_id'], self.options[bi['option_id']]['service_id'])

        # Sub-order vendor and service alignment
        for so in self.data['sub_orders']:
            self.assertIn(so['vendor_id'], self.vendors)
            self.assertIn(so['service_id'], self.services)
            self.assertEqual(self.services[so['service_id']]['vendor_id'], so['vendor_id'])

        # Admin user check in payout requests
        for pr in self.data['payout_requests']:
            self.assertIn(pr['processed_by'], self.users)
            self.assertEqual('ADMIN', self.users[pr['processed_by']]['role'])

    def test_internal_transaction_linkages(self):
        booking_ids = {b['id'] for b in self.data['bookings']}
        booking_item_ids = {bi['id'] for bi in self.data['booking_items']}
        master_order_ids = {mo['id'] for mo in self.data['master_orders']}
        sub_order_ids = {so['id'] for so in self.data['sub_orders']}
        settlement_ids = {s['id'] for s in self.data['settlements']}

        for bi in self.data['booking_items']:
            self.assertIn(bi['booking_id'], booking_ids)

        for mo in self.data['master_orders']:
            self.assertIn(mo['booking_id'], booking_ids)

        for so in self.data['sub_orders']:
            self.assertIn(so['master_order_id'], master_order_ids)
            self.assertIn(so['booking_item_id'], booking_item_ids)

        for p in self.data['payments']:
            self.assertIn(p['master_order_id'], master_order_ids)

        for r in self.data['refunds']:
            self.assertIn(r['sub_order_id'], sub_order_ids)

        for inv in self.data['invoices']:
            self.assertIn(inv['master_order_id'], master_order_ids)

        for sli in self.data['settlement_line_items']:
            self.assertIn(sli['settlement_id'], settlement_ids)
            self.assertIn(sli['sub_order_id'], sub_order_ids)

        for pr in self.data['payout_requests']:
            self.assertIn(pr['settlement_id'], settlement_ids)

    def test_financial_calculations(self):
        for so in self.data['sub_orders']:
            unit_price = Decimal(so['unit_price'])
            qty = Decimal(so['quantity'])
            subtotal = Decimal(so['subtotal_amount'])
            self.assertEqual(unit_price * qty, subtotal, 'Subtotal must equal unit_price * quantity')

            discount = Decimal(so['discount_amount'])
            final = Decimal(so['final_amount'])
            self.assertEqual(subtotal - discount, final, 'Final amount must equal subtotal - discount')

            rate = Decimal(so['commission_rate'])
            comm = Decimal(so['commission_amount'])
            expected_comm = (final * rate).quantize(Decimal('0.01'))
            self.assertEqual(expected_comm, comm, 'Commission must equal final * commission_rate')

            payout = Decimal(so['vendor_payout_amount'])
            self.assertEqual(final - comm, payout, 'Vendor payout must equal final - commission')

        # Settlement line item and aggregate checks
        line_items_by_settlement = {}
        for sli in self.data['settlement_line_items']:
            gross = Decimal(sli['gross_amount'])
            ref = Decimal(sli['refund_amount'])
            comm = Decimal(sli['commission_amount'])
            net = Decimal(sli['net_amount'])
            so = next(s for s in self.data['sub_orders'] if s['id'] == sli['sub_order_id'])
            basis = Decimal(so['commission_basis_amount'])
            reversed_basis = (basis * ref / Decimal(so['final_amount'])).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
            self.assertEqual(basis - reversed_basis, gross)
            self.assertEqual((gross * Decimal(sli['commission_rate'])).quantize(Decimal('0.01')), comm)
            self.assertEqual(gross - comm, net, 'Post-refund gross must not deduct refund twice')
            line_items_by_settlement.setdefault(sli['settlement_id'], []).append(sli)

        for s in self.data['settlements']:
            items = line_items_by_settlement[s['id']]
            expected_gross = sum(Decimal(i['gross_amount']) for i in items)
            expected_comm = sum(Decimal(i['commission_amount']) for i in items)
            expected_ref = sum(Decimal(i['refund_amount']) for i in items)
            expected_net = sum(Decimal(i['net_amount']) for i in items)

            self.assertEqual(expected_gross, Decimal(s['gross_amount']))
            self.assertEqual(expected_comm, Decimal(s['commission_amount']))
            self.assertEqual(expected_net, Decimal(s['net_payable_amount']))

    def test_unit_prices_dates_and_customer_ownership(self):
        for booking in self.data['bookings']:
            items = [i for i in self.data['booking_items'] if i['booking_id'] == booking['id']]
            self.assertEqual(Decimal(booking['total_amount']), sum(Decimal(i['price']) * i['quantity'] for i in items))
            self.assertEqual(timedelta(minutes=15), datetime.fromisoformat(booking['hold_expires_at']) - datetime.fromisoformat(booking['created_at']))
            for item in items:
                self.assertEqual(item['booking_date'], self.slots[item['slot_id']]['date'])
                self.assertEqual(item['vendor_id'], self.services[item['service_id']]['vendor_id'])
                start = datetime.fromisoformat(item['booking_date'] + 'T' + item['booking_time'] + '+07:00')
                self.assertLess(datetime.fromisoformat(booking['created_at']), start)
        for order in self.data['master_orders']:
            booking = next(b for b in self.data['bookings'] if b['id'] == order['booking_id'])
            self.assertEqual(booking['customer_id'], order['customer_id'])
            self.assertEqual(Decimal(booking['total_amount']), Decimal(order['total_amount']))

    def test_refund_payment_links_and_payout_balance(self):
        for refund in self.data['refunds']:
            self.assertIn('payment_id', refund, 'Refund must link its original payment')
            payment = next(p for p in self.data['payments'] if p['id'] == refund['payment_id'])
            so = next(s for s in self.data['sub_orders'] if s['id'] == refund['sub_order_id'])
            self.assertEqual(payment['master_order_id'], so['master_order_id'])
            self.assertEqual(payment['provider'], refund['provider'])
            self.assertEqual(Decimal(so['final_amount']) * Decimal(refund['refund_percentage']) / 100, Decimal(refund['amount']))
        for settlement in self.data['settlements']:
            payouts = [p for p in self.data['payout_requests'] if p['settlement_id'] == settlement['id'] and p['status'] != 'REJECTED']
            self.assertLessEqual(sum(Decimal(p['amount']) for p in payouts), Decimal(settlement['net_payable_amount']))
            self.assertTrue(all(p['vendor_id'] == settlement['vendor_id'] for p in payouts))

    def test_checkin_window_and_no_fabricated_gateway_evidence(self):
        for so in self.data['sub_orders']:
            if so['checked_in_at']:
                item = next(i for i in self.data['booking_items'] if i['id'] == so['booking_item_id'])
                start = datetime.fromisoformat(item['booking_date'] + 'T' + item['booking_time'] + '+07:00')
                checkin = datetime.fromisoformat(so['checked_in_at'])
                self.assertLessEqual(start - timedelta(hours=2), checkin)
                end = datetime.fromisoformat(item['booking_date'] + 'T' + self.slots[item['slot_id']]['end_time'] + '+07:00')
                self.assertLessEqual(checkin, end + timedelta(minutes=30))
        for p in self.data['payments']:
            self.assertTrue(all(p[k] is None for k in ('provider_transaction_id', 'provider_order_id', 'payment_url', 'qr_code_url', 'raw_webhook_payload', 'webhook_event_id', 'last_reconciled_at')))
            self.assertEqual(0, p['reconciliation_attempts'])
            self.assertNotEqual('PENDING', p['status'], 'Historical mock intents must not enter gateway jobs')
        self.assertTrue(all(i['pdf_url'] is None and i['invoice_number'].startswith('SYNTHETIC-') for i in self.data['invoices']))

    def test_domain_enum_compliance(self):
        valid_providers = {'VNPAY', 'MOMO', 'SEPAY', 'PAYPAL'}
        valid_booking_status = {'HOLD', 'PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'REJECTED'}
        valid_order_status = {'PENDING_PAYMENT', 'PAID', 'PARTIALLY_COMPLETED', 'COMPLETED', 'CANCELLED'}
        valid_payment_order_status = {'UNPAID', 'PAID', 'REFUNDED', 'NO_REFUND'}
        valid_payment_status = {'PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'}
        valid_sub_order_status = {'PENDING', 'CONFIRMED', 'REJECTED', 'COMPLETED', 'CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED', 'CHECKED_IN', 'IN_PROGRESS'}
        valid_settlement_status = {'DRAFT', 'FINALIZED', 'PAID', 'PENDING'}
        valid_payout_status = {'REQUESTED', 'APPROVED', 'PAID', 'REJECTED'}
        valid_refund_reasons = {'CUSTOMER_REQUEST', 'CUSTOMER_CANCEL', 'WEATHER', 'VENDOR_FAULT', 'SYSTEM_FAULT', 'ADMIN_OVERRIDE', 'DISPUTE', 'COMPENSATION'}

        for b in self.data['bookings']:
            self.assertIn(b['status'], valid_booking_status)
        for mo in self.data['master_orders']:
            self.assertIn(mo['status'], valid_order_status)
            self.assertIn(mo['payment_status'], valid_payment_order_status)
        for so in self.data['sub_orders']:
            self.assertIn(so['status'], valid_sub_order_status)
            if so['cancellation_reason']:
                self.assertIn(so['cancellation_reason'], valid_refund_reasons)
        for p in self.data['payments']:
            self.assertIn(p['provider'], valid_providers)
            self.assertIn(p['status'], valid_payment_status)
        for r in self.data['refunds']:
            self.assertIn(r['reason'], valid_refund_reasons)
        for s in self.data['settlements']:
            self.assertIn(s['status'], valid_settlement_status)
        for pr in self.data['payout_requests']:
            self.assertIn(pr['status'], valid_payout_status)


if __name__ == '__main__':
    unittest.main()
