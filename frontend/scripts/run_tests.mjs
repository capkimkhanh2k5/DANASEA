import fs from 'node:fs';
import path from 'node:path';
import { createJiti } from 'jiti';

const rootDir = path.resolve(import.meta.dirname, '..');
const srcDir = path.join(rootDir, 'src');
const jiti = createJiti(path.join(rootDir, 'package.json'), { jsx: true });

function collectTestFiles(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectTestFiles(fullPath));
    } else if (entry.name.endsWith('.test.ts') || entry.name.endsWith('.test.tsx')) {
      files.push(fullPath);
    }
  }
  return files;
}

for (const testFile of collectTestFiles(srcDir).sort()) {
  jiti(testFile);
}
