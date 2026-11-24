#!/usr/bin/env node
/**
 * DANASEA ANTI-SLOP & HARNESS ENFORCEMENT AUDIT (Rule 8, 9, 10, 16)
 *
 * Automated AST & Pattern Enforcement Gate:
 * - Scans all frontend files for banned AI slop, redundant subtitles, tutorial callouts,
 *   bilingual boilerplate in parentheses, and fake status capsules.
 * - Exits with code 1 on ANY violation to prevent commits and force compliance.
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const SRC_DIR = path.resolve(__dirname, '../src');

// Violation Rules Definition (Integrated from taste-skill, impeccable, and miqdadbadjuber/anti-slop)
const AUDIT_RULES = [
  {
    id: 'SLOP-001-HEADER-SUBTITLE',
    name: 'Redundant AtlasPageHeader Subtitle',
    description: 'Banned obvious explanatory subtitles under page headers (Rule 8).',
    test: (content) => {
      const violations = [];
      const regex = /<AtlasPageHeader[\s\S]*?subtitle=(?:["'{]([^"'}]*)["'}])/g;
      let match;
      while ((match = regex.exec(content)) !== null) {
        const subtitle = match[1];
        if (
          /theo dõi|tiếp nhận|cấu hình|quản lý|khai báo|cho phép|giúp bạn|danh sách/i.test(subtitle) &&
          !/mã|id|lỗi|cảnh báo|#/i.test(subtitle)
        ) {
          violations.push({ text: match[0], detail: `Redundant subtitle: "${subtitle}"` });
        }
      }
      return violations;
    },
  },
  {
    id: 'SLOP-002-BILINGUAL-PARENS',
    name: 'Bilingual / Code Parenthetical Boilerplate',
    description: 'Banned English / enum parentheticals in UI headers & labels (Rule 8, 9).',
    test: (content) => {
      const violations = [];
      const patterns = [
        /\(Pricing Options\)/gi,
        /\(Waiver\)/gi,
        /\(Latitude\)/gi,
        /\(Longitude\)/gi,
        /\(SHARED\)/gi,
        /\(PRIVATE\)/gi,
        /\(PER_PACKAGE\)/gi,
        /\(PER_PERSON\)/gi,
      ];
      for (const p of patterns) {
        let match;
        while ((match = p.exec(content)) !== null) {
          violations.push({ text: match[0], detail: `Bilingual parenthetical boilerplate: "${match[0]}"` });
        }
      }
      return violations;
    },
  },
  {
    id: 'SLOP-003-TUTORIAL-BANNER',
    name: 'Patronizing Tutorial Callouts / Mẹo',
    description: 'Banned patronizing tutorial callouts and obvious tips (Rule 8).',
    test: (content) => {
      const violations = [];
      const regex = /(?:Mẹo:|Hướng dẫn:|Lưu ý:\s*Bạn có thể)[\s\S]{0,100}/g;
      let match;
      while ((match = regex.exec(content)) !== null) {
        violations.push({ text: match[0], detail: `Patronizing callout box found: "${match[0].slice(0, 50)}..."` });
      }
      return violations;
    },
  },
  {
    id: 'SLOP-004-LINGUISTIC-SLOP',
    name: 'Banned AI Poetic Buzzwords (Rule 9, taste-skill tells)',
    description: 'Banned artificial AI metaphors and empty buzzwords.',
    test: (content) => {
      const violations = [];
      const bannedPhrases = [
        'Bản giao hưởng',
        'Bức tranh đa sắc',
        'Chạm vào sóng',
        'Hành trình chữa lành',
        'Khoảng thở',
        'Nâng tầm trải nghiệm',
        'Đánh thức mọi giác quan',
        'tapestry of',
        'testament to',
        'symphony of',
        'delve into',
        'beacon of',
        'pivotal role',
      ];
      for (const phrase of bannedPhrases) {
        if (content.includes(phrase)) {
          violations.push({ text: phrase, detail: `Banned AI slop buzzword: "${phrase}"` });
        }
      }
      return violations;
    },
  },
  {
    id: 'SLOP-005-FAKE-STATUS-CAPSULE',
    name: 'Fake Decorative System Status Capsule',
    description: 'Banned fake decorative online/offline capsules in headers (Rule 8).',
    test: (content) => {
      const violations = [];
      const regex = /●\s*(?:Cảng vụ trực tuyến|Hệ thống trực tuyến|Trực tuyến sẵn sàng)/gi;
      let match;
      while ((match = regex.exec(content)) !== null) {
        violations.push({ text: match[0], detail: `Fake decorative status capsule: "${match[0]}"` });
      }
      return violations;
    },
  },
  {
    id: 'SLOP-006-AI-PURPLE-GRADIENT',
    name: 'Banned Neon & Purple Gradients (taste-skill palette-purple & gradient-text)',
    description: 'Banned AI signature purple/violet gradient text or buttons (Rule 6).',
    test: (content, relPath) => {
      // Exclude tests or mock configs if any
      const violations = [];
      const regex = /(?:from-purple-|from-violet-|to-purple-|to-violet-)[\s\S]*?(?:bg-clip-text|text-transparent)/g;
      let match;
      while ((match = regex.exec(content)) !== null) {
        violations.push({ text: match[0], detail: `Banned AI purple gradient text: "${match[0]}"` });
      }
      return violations;
    },
  },
  {
    id: 'SLOP-007-DECORATIVE-INDEXING',
    name: 'Decorative Section Indexing (taste-skill numbering)',
    description: 'Banned decorative index numbers like Chapter 01, 01 / 02 in titles.',
    test: (content) => {
      const violations = [];
      const regex = /(?:Chapter\s*0\d|01\s*\/\s*0\d)\b/gi;
      let match;
      while ((match = regex.exec(content)) !== null) {
        violations.push({ text: match[0], detail: `Banned decorative index number: "${match[0]}"` });
      }
      return violations;
    },
  },
  {
    id: 'SLOP-008-METRIC-SUBTEXT-FILLER',
    name: 'Trivial Metric Subtext Filler (Rule 8)',
    description: 'Banned trivial explanatory subtext under AtlasMetricCard.',
    test: (content) => {
      const violations = [];
      const patterns = [
        /Thang điểm 5\.0 tối đa/gi,
        /Đã chốt số liệu/gi,
        /Toàn gian hàng & chỉ định/gi,
        /Đối soát trực tiếp qua hệ thống DANASEA/gi,
      ];
      for (const p of patterns) {
        let match;
        while ((match = p.exec(content)) !== null) {
          violations.push({ text: match[0], detail: `Trivial metric subtext: "${match[0]}"` });
        }
      }
      return violations;
    },
  },
  {
    id: 'SLOP-009-THICK-ACCENT-BORDER',
    name: 'Thick Colored Side Border (impeccable craft-floor)',
    description: 'Banned colored border-left/border-right above 1px on cards/callouts.',
    test: (content) => {
      const violations = [];
      const regex = /\b(?:border-l-4|border-l-8|border-r-4|border-r-8)\s+border-(?:blue|emerald|amber|red|sky|indigo|teal)-/g;
      let match;
      while ((match = regex.exec(content)) !== null) {
        violations.push({ text: match[0], detail: `Banned thick colored card side border: "${match[0]}"` });
      }
      return violations;
    },
  },
];

// Recursive scanner for frontend files
function scanDirectory(dir, fileList = []) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const fullPath = path.join(dir, file);
    const stat = fs.statSync(fullPath);
    if (stat.isDirectory()) {
      if (file !== 'node_modules' && file !== 'dist' && file !== '.git') {
        scanDirectory(fullPath, fileList);
      }
    } else if (/\.(tsx|jsx|ts|js)$/.test(file)) {
      fileList.push(fullPath);
    }
  }
  return fileList;
}

// Main execution
console.log('\n🔍 [HARNESS AUDIT] Scanning frontend source files for Anti-Slop compliance...');

const allFiles = scanDirectory(SRC_DIR);
let totalViolations = 0;
const results = [];

for (const filePath of allFiles) {
  const relPath = path.relative(path.resolve(SRC_DIR, '..'), filePath).replace(/\\/g, '/');
  const content = fs.readFileSync(filePath, 'utf-8');

  for (const rule of AUDIT_RULES) {
    const findings = rule.test(content);
    if (findings.length > 0) {
      totalViolations += findings.length;
      results.push({
        file: relPath,
        ruleId: rule.id,
        ruleName: rule.name,
        findings,
      });
    }
  }
}

if (totalViolations === 0) {
  console.log(`\n✅ [HARNESS AUDIT PASSED] 0 Anti-Slop violations across ${allFiles.length} files.`);
  console.log('   All UI headers, copy, and layout geometry satisfy AGENTS.md rules.\n');
  process.exit(0);
} else {
  console.error(`\n❌ [HARNESS AUDIT FAILED] Found ${totalViolations} Anti-Slop violation(s)!`);
  console.error('   Strict compliance with AGENTS.md Rule 8 & 9 is mandatory.\n');

  for (const res of results) {
    console.error(`📁 File: ${res.file}`);
    console.error(`   Rule: [${res.ruleId}] ${res.ruleName}`);
    for (const f of res.findings) {
      console.error(`   - ${f.detail}`);
    }
    console.error('');
  }

  console.error('👉 Action required: Fix all violations above before committing or finishing.\n');
  process.exit(1);
}
