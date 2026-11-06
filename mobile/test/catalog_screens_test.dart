import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/data/catalog_repository.dart';
import 'package:mobile/core/models/danasea_models.dart';
import 'package:mobile/features/search/presentation/screens/search_screen.dart';
import 'package:mobile/features/experience_detail/presentation/screens/catalog_detail_screen.dart';

ServiceModel service(String id) =>
    CatalogRepository.serviceFromJson({'id': id, 'name': id, 'price': 300000});

class FakeCatalog extends CatalogRepository {
  final pending = <Completer<CatalogPage>>[];
  final pages = <int>[];
  bool failDetail = false;
  @override
  Future<List<CategoryModel>> categories() async => [];
  @override
  Future<CatalogPage> search({
    String? keyword,
    String? categoryId,
    int? maxPrice,
    int page = 0,
    int size = 10,
    String? sortBy,
  }) {
    pages.add(page);
    final completer = Completer<CatalogPage>();
    pending.add(completer);
    return completer.future;
  }

  @override
  Future<ServiceModel> detail(String id) async {
    if (failDetail) throw Exception('Unavailable');
    return service(id);
  }
}

void main() {
  testWidgets('Late search cannot replace newest query; next page appends', (
    tester,
  ) async {
    final repo = FakeCatalog();
    await tester.pumpWidget(MaterialApp(home: SearchScreen(repository: repo)));
    await tester.enterText(find.byType(TextField), 'new');
    await tester.pump(const Duration(milliseconds: 400));
    repo.pending[1].complete(CatalogPage([service('new-result')], 0, 2));
    await tester.pumpAndSettle();
    repo.pending[0].complete(CatalogPage([service('old-result')], 0, 1));
    await tester.pumpAndSettle();
    expect(find.text('new-result'), findsOneWidget);
    expect(find.text('old-result'), findsNothing);
    await tester.tap(find.byType(TextButton));
    await tester.pump();
    expect(repo.pages.last, 1);
    repo.pending.last.complete(CatalogPage([service('second-result')], 1, 2));
    await tester.pumpAndSettle();
    expect(find.text('new-result'), findsOneWidget);
    expect(find.text('second-result'), findsOneWidget);
  });
  testWidgets('Detail failure can retry and render server service', (
    tester,
  ) async {
    final repo = FakeCatalog()..failDetail = true;
    await tester.pumpWidget(
      MaterialApp(
        home: CatalogDetailScreen(serviceId: 'real-service', repository: repo),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Exception: Unavailable'), findsOneWidget);
    repo.failDetail = false;
    await tester.tap(find.byType(TextButton));
    await tester.pumpAndSettle();
    expect(find.text('real-service'), findsOneWidget);
    expect(find.text('Exception: Unavailable'), findsNothing);
  });
}
