#!/usr/bin/env node
/**
 * DANASEA FULL-SUITE TASTE & ANTI-SLOP GATE
 *
 * Enforces 100% compliance with both:
 * 1. Taste-Skill Official Linter (.agents/skills/taste-skill/scripts/lint.mjs)
 * 2. Danasea Anti-Slop Audit (frontend/scripts/anti_slop_audit.mjs)
 *
 * Usage:
 *   node scripts/taste_gate.mjs [target_path]
 */

import { spawnSync } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const TARGET = process.argv[2] || 'src';

console.log(`\n🛡️ [TASTE & SLOP GATE] Running full-matrix verification on: ${TARGET}...\n`);

// Gate 1: Custom Danasea Anti-Slop Audit
console.log('--- [GATE 1/2] Running Danasea Anti-Slop Audit ---');
const slopRes = spawnSync('node', ['scripts/anti_slop_audit.mjs'], {
  stdio: 'inherit',
  cwd: path.resolve(__dirname, '..'),
});

if (slopRes.status !== 0) {
  console.error('\n❌ [GATE 1 FAILED] Anti-Slop violations detected! Aborting.\n');
  process.exit(1);
}

// Gate 2: Official Taste-Skill Linter
console.log('\n--- [GATE 2/2] Running Official Taste-Skill Linter ---');
const lintScript = path.resolve(__dirname, '../../.agents/skills/taste-skill/scripts/lint.mjs');
const tasteRes = spawnSync('node', [lintScript, TARGET, '--app'], {
  stdio: 'inherit',
  cwd: path.resolve(__dirname, '..'),
});

if (tasteRes.status !== 0) {
  console.error('\n❌ [GATE 2 FAILED] Taste-Skill rules violated! Aborting.\n');
  process.exit(1);
}

console.log('\n✅ [TASTE & SLOP GATE PASSED] 100% of checks from both repositories passed with 0 FAIL findings!\n');
process.exit(0);
