import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { AdminPagination } from '../AdminWorkspace.tsx';

describe('AdminPagination — Vendor-styled Navigation Controls', () => {
  test('returns null when totalRecords is 0', () => {
    const html = renderToStaticMarkup(
      React.createElement(AdminPagination, {
        currentPage: 1,
        totalPages: 0,
        totalRecords: 0,
        onPageChange: () => {},
      })
    );
    assert.equal(html, '');
  });

  test('on first page: First and Prev buttons are disabled, Next and Last are enabled', () => {
    const html = renderToStaticMarkup(
      React.createElement(AdminPagination, {
        currentPage: 1,
        totalPages: 5,
        totalRecords: 40,
        pageSize: 8,
        onPageChange: () => {},
      })
    );

    // Displays current page 1
    assert.ok(html.includes('aria-label="Trang hiện tại 1"'));
    assert.ok(html.includes('>1</button>'));

    // Check first/prev disabled
    assert.match(html, /<button[^>]*disabled=""[^>]*aria-label="Về trang đầu"/);
    assert.match(html, /<button[^>]*disabled=""[^>]*aria-label="Trang trước"/);

    // Check next/last enabled
    assert.doesNotMatch(html, /<button[^>]*disabled=""[^>]*aria-label="Trang sau"/);
    assert.doesNotMatch(html, /<button[^>]*disabled=""[^>]*aria-label="Về trang cuối"/);

    // Check record count info
    assert.ok(html.includes('Hiển thị <strong>1–8</strong> / <strong>40</strong> bản ghi'));
  });

  test('on middle page: all 4 navigation buttons are enabled', () => {
    const html = renderToStaticMarkup(
      React.createElement(AdminPagination, {
        currentPage: 3,
        totalPages: 5,
        totalRecords: 40,
        pageSize: 8,
        onPageChange: () => {},
      })
    );

    assert.ok(html.includes('aria-label="Trang hiện tại 3"'));
    assert.ok(html.includes('>3</button>'));

    // None of the 4 buttons should be disabled
    assert.doesNotMatch(html, /<button[^>]*disabled=""[^>]*aria-label="Về trang đầu"/);
    assert.doesNotMatch(html, /<button[^>]*disabled=""[^>]*aria-label="Trang trước"/);
    assert.doesNotMatch(html, /<button[^>]*disabled=""[^>]*aria-label="Trang sau"/);
    assert.doesNotMatch(html, /<button[^>]*disabled=""[^>]*aria-label="Về trang cuối"/);

    assert.ok(html.includes('Hiển thị <strong>17–24</strong> / <strong>40</strong> bản ghi'));
  });

  test('on last page: First and Prev are enabled, Next and Last are disabled', () => {
    const html = renderToStaticMarkup(
      React.createElement(AdminPagination, {
        currentPage: 5,
        totalPages: 5,
        totalRecords: 38,
        pageSize: 8,
        onPageChange: () => {},
      })
    );

    assert.ok(html.includes('aria-label="Trang hiện tại 5"'));
    assert.ok(html.includes('>5</button>'));

    // First/prev enabled
    assert.doesNotMatch(html, /<button[^>]*disabled=""[^>]*aria-label="Về trang đầu"/);
    assert.doesNotMatch(html, /<button[^>]*disabled=""[^>]*aria-label="Trang trước"/);

    // Next/last disabled
    assert.match(html, /<button[^>]*disabled=""[^>]*aria-label="Trang sau"/);
    assert.match(html, /<button[^>]*disabled=""[^>]*aria-label="Về trang cuối"/);

    assert.ok(html.includes('Hiển thị <strong>33–38</strong> / <strong>38</strong> bản ghi'));
  });

  test('on single page: all 4 navigation buttons are disabled', () => {
    const html = renderToStaticMarkup(
      React.createElement(AdminPagination, {
        currentPage: 1,
        totalPages: 1,
        totalRecords: 5,
        pageSize: 8,
        onPageChange: () => {},
      })
    );

    assert.ok(html.includes('aria-label="Trang hiện tại 1"'));
    assert.ok(html.includes('>1</button>'));

    // All 4 buttons must be disabled
    assert.match(html, /<button[^>]*disabled=""[^>]*aria-label="Về trang đầu"/);
    assert.match(html, /<button[^>]*disabled=""[^>]*aria-label="Trang trước"/);
    assert.match(html, /<button[^>]*disabled=""[^>]*aria-label="Trang sau"/);
    assert.match(html, /<button[^>]*disabled=""[^>]*aria-label="Về trang cuối"/);

    assert.ok(html.includes('Hiển thị <strong>1–5</strong> / <strong>5</strong> bản ghi'));
  });

  test('safely clamps out-of-bounds current page', () => {
    // Current page 99 clamped to 5
    const htmlOver = renderToStaticMarkup(
      React.createElement(AdminPagination, {
        currentPage: 99,
        totalPages: 5,
        totalRecords: 40,
        pageSize: 8,
        onPageChange: () => {},
      })
    );
    assert.ok(htmlOver.includes('aria-label="Trang hiện tại 5"'));

    // Current page -5 clamped to 1
    const htmlUnder = renderToStaticMarkup(
      React.createElement(AdminPagination, {
        currentPage: -5,
        totalPages: 5,
        totalRecords: 40,
        pageSize: 8,
        onPageChange: () => {},
      })
    );
    assert.ok(htmlUnder.includes('aria-label="Trang hiện tại 1"'));
  });
});
