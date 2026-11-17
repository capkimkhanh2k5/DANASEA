import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { parseBlocks, renderInline, INLINE_REGEX } from '../markdownParser';

describe('MarkdownText Parser & Renderer', () => {
  it('parses empty and null strings safely', () => {
    assert.deepEqual(parseBlocks(''), []);
    assert.deepEqual(parseBlocks(null as unknown as string), []);
    assert.deepEqual(renderInline('', 'default'), []);
  });

  it('correctly groups bullet points into bullet-list blocks', () => {
    const raw = `
Theo thông tin cập nhật cho khu vực biển Mỹ Khê (Đà Nẵng) hôm nay (16/11/2026):

- **Tình trạng an toàn & biển:** Mức cảnh báo **GREEN** (An toàn). Trạng thái: *Thời tiết tốt, an toàn xuất bến.*
- **Dự báo chi tiết:** Dữ liệu dự báo thời tiết chi tiết tạm thời không khả dụng trên hệ thống lúc này.

Bạn có muốn tìm kiếm các dịch vụ, tour biển hay hoạt động giải trí nào tại Đà Nẵng cho chuyến đi của mình không?
    `.trim();

    const blocks = parseBlocks(raw);
    assert.equal(blocks.length, 3);
    assert.equal(blocks[0].type, 'p');
    assert.equal(blocks[1].type, 'bullet-list');
    if (blocks[1].type === 'bullet-list') {
      assert.equal(blocks[1].items.length, 2);
      assert.ok(blocks[1].items[0].includes('**Tình trạng an toàn & biển:**'));
      assert.ok(blocks[1].items[1].includes('**Dự báo chi tiết:**'));
    }
    assert.equal(blocks[2].type, 'p');
  });

  it('correctly groups numbered lists into numbered-list blocks', () => {
    const raw = `
1. Bước 1: Khởi hành tại bến Mỹ Khê
2. Bước 2: Trang bị áo phao SOLAS
    `.trim();

    const blocks = parseBlocks(raw);
    assert.equal(blocks.length, 1);
    assert.equal(blocks[0].type, 'numbered-list');
    if (blocks[0].type === 'numbered-list') {
      assert.equal(blocks[0].items.length, 2);
      assert.equal(blocks[0].items[0].num, '1');
      assert.equal(blocks[0].items[1].num, '2');
    }
  });

  it('correctly extracts code blocks with optional language', () => {
    const raw = '```json\n{"status":"ok"}\n```';
    const blocks = parseBlocks(raw);
    assert.equal(blocks.length, 1);
    assert.equal(blocks[0].type, 'code');
    if (blocks[0].type === 'code') {
      assert.equal(blocks[0].lang, 'json');
      assert.equal(blocks[0].code, '{"status":"ok"}');
    }
  });

  it('correctly handles headings h1, h2, h3', () => {
    const raw = '# Tiêu đề 1\n## Tiêu đề 2\n### Tiêu đề 3';
    const blocks = parseBlocks(raw);
    assert.equal(blocks.length, 3);
    assert.equal(blocks[0].type, 'h1');
    assert.equal(blocks[1].type, 'h2');
    assert.equal(blocks[2].type, 'h3');
  });

  it('correctly matches inline bold, italic, code, and links with INLINE_REGEX', () => {
    const text = 'Xem **báo cáo** và *ghi chú*, dùng lệnh `npm test` hoặc vào [DANASEA](https://danasea.vn).';
    const tokens = text.split(INLINE_REGEX).filter(Boolean);

    assert.ok(tokens.includes('**báo cáo**'));
    assert.ok(tokens.includes('*ghi chú*'));
    assert.ok(tokens.includes('`npm test`'));
    assert.ok(tokens.includes('[DANASEA](https://danasea.vn)'));
  });

  it('renders inline react nodes for bold, italic, code, and links', () => {
    const text = 'Mức cảnh báo **GREEN** và *an toàn* kèm `mã vé`';
    const nodes = renderInline(text, 'default');
    assert.ok(nodes.length >= 3);
  });
});
