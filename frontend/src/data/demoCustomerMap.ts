/**
 * Authoritative Customer Lookup Table
 * Extracted directly from DB_Demo/base/users.json & demo_transactions.json
 * Provides accurate customer identification for booking items and sub-orders.
 */

export interface CustomerInfo {
  name: string;
  phone: string;
  email: string;
}

export const authoritativeCustomerMap: Record<string, CustomerInfo> = {
  "b0020000-0000-0000-0000-000000000001": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000002": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000003": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000004": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000005": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000006": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000007": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000008": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000009": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000010": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000011": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000012": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000013": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000014": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000015": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000016": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000017": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000018": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000019": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000020": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000021": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000022": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000023": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000024": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000025": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000026": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000027": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000028": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000029": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000030": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000031": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000032": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000033": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000034": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000035": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000036": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000037": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000038": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000039": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000040": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000041": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000042": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000043": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000044": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000045": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000046": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000047": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000048": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000049": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "b0020000-0000-0000-0000-000000000050": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000001": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000002": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000003": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000004": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000005": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000006": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000007": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000008": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000009": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000010": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000011": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000012": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000013": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000014": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000015": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000016": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000017": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000018": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000019": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000020": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000021": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000022": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000023": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000024": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000025": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000026": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000027": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000028": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000029": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000030": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000031": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000032": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000033": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000034": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000035": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000036": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000037": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000038": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000039": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000040": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000041": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000042": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000043": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000044": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000045": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000046": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000047": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000048": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  },
  "00020000-0000-0000-0000-000000000049": {
    "name": "Nguyễn Hoàng Long",
    "phone": "0912345678",
    "email": "customer_1@danasea.test"
  },
  "00020000-0000-0000-0000-000000000050": {
    "name": "Trần Thị Mai Anh",
    "phone": "0988765432",
    "email": "customer_2@danasea.test"
  }
};

export function getAuthoritativeCustomer(id?: string): CustomerInfo | null {
  if (!id) return null;
  return authoritativeCustomerMap[id] || null;
}
