import 'package:flutter/foundation.dart';
import '../../../../core/models/danasea_models.dart';
import '../presentation/screens/cart_screen.dart';

class CartManager extends ChangeNotifier {
  static final CartManager instance = CartManager._internal();
  CartManager._internal();

  final List<CartItemData> _items = [];
  List<CartItemData> get items => _items;

  int get count => _items.length;

  void addItem({
    required ServiceModel service,
    required CatalogSlot slot,
    required int quantity,
  }) {
    final existingIndex = _items.indexWhere(
      (item) => item.service.id == service.id && item.slot.id == slot.id,
    );
    if (existingIndex >= 0) {
      _items[existingIndex].quantity += quantity;
    } else {
      _items.add(
        CartItemData(
          id: 'cart_${DateTime.now().millisecondsSinceEpoch}',
          service: service,
          slot: ServiceSlotModel(
            id: slot.id,
            serviceId: service.id,
            date: DateTime.tryParse(slot.date) ?? DateTime.now(),
            startTime: slot.startTime,
            endTime: slot.endTime,
            capacity: slot.availableCapacity ?? 30,
            bookedCount: 0,
          ),
          quantity: quantity,
          isSelected: true,
        ),
      );
    }
    notifyListeners();
  }

  void removeItem(String id) {
    _items.removeWhere((item) => item.id == id);
    notifyListeners();
  }

  void clear() {
    _items.clear();
    notifyListeners();
  }
}
