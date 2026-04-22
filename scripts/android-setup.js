#!/usr/bin/env node
/**
 * android-setup.js
 *
 * Automatically patches the host app's Android project when
 * `npm install capacitor-hyperswitch` (or yarn add) is run.
 *
 * Changes applied:
 *  1. android/build.gradle        — adds Hyperswitch Gradle plugin classpath
 *  2. android/app/build.gradle    — applies io.hyperswitch.plugin
 *  3. MainActivity.java           — implements DefaultHardwareBackBtnHandler
 *
 * npm sets INIT_CWD to the directory where `npm install` was invoked,
 * i.e. the consumer's project root. We fall back to navigating up from
 * __dirname when running outside npm (e.g. in CI or manual testing).
 */

'use strict';

const fs = require('fs');
const path = require('path');

// ── Locate the consumer's project root ────────────────────────────────────────

// INIT_CWD  = directory where the user ran `npm install`  (set by npm / yarn)
// Fallback  = node_modules/capacitor-hyperswitch/scripts/ → up 3 levels
const projectRoot = process.env.INIT_CWD || path.resolve(__dirname, '../../..');

const androidDir = path.join(projectRoot, 'android');

if (!fs.existsSync(androidDir)) {
  console.log('[capacitor-hyperswitch] No android/ directory found — skipping Android setup.');
  process.exit(0);
}

let patchCount = 0;

// ── Helper ────────────────────────────────────────────────────────────────────

function patch(label, filePath, fn) {
  if (!fs.existsSync(filePath)) {
    console.warn(`[capacitor-hyperswitch] ⚠ ${label} not found at ${filePath} — skipping.`);
    return;
  }

  const original = fs.readFileSync(filePath, 'utf8');
  const updated = fn(original);

  if (updated === original) {
    console.log(`[capacitor-hyperswitch] ✔ ${label} already up to date.`);
    return;
  }

  fs.writeFileSync(filePath, updated, 'utf8');
  console.log(`[capacitor-hyperswitch] ✔ Patched ${label}.`);
  patchCount++;
}

// ── 1. android/build.gradle — add Hyperswitch Gradle plugin classpath ─────────

patch('android/build.gradle', path.join(androidDir, 'build.gradle'), (content) => {
  if (content.includes('io.hyperswitch:hyperswitch-gradle-plugin')) return content;

  // Insert on a new line immediately after the AGP classpath line.
  return content.replace(
    /([ \t]*classpath\s+['"]com\.android\.tools\.build:gradle[^'"]*['"]\s*\n)/,
    `$1        classpath "io.hyperswitch:hyperswitch-gradle-plugin:0.3.0"\n`,
  );
});

// ── 2. android/app/build.gradle — apply io.hyperswitch.plugin ─────────────────

patch('android/app/build.gradle', path.join(androidDir, 'app', 'build.gradle'), (content) => {
  if (content.includes("apply plugin: 'io.hyperswitch.plugin'")) return content;

  // Insert right after `apply plugin: 'com.android.application'`.
  return content.replace(
    /(apply plugin: 'com\.android\.application'\s*\n)/,
    `$1apply plugin: 'io.hyperswitch.plugin'\n`,
  );
});

// ── 3. MainActivity.java — implement DefaultHardwareBackBtnHandler ────────────

function findFile(dir, filename) {
  if (!fs.existsSync(dir)) return null;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      const found = findFile(full, filename);
      if (found) return found;
    } else if (entry.name === filename) {
      return full;
    }
  }
  return null;
}

const mainActivityPath = findFile(path.join(androidDir, 'app', 'src', 'main', 'java'), 'MainActivity.java');

if (!mainActivityPath) {
  console.warn('[capacitor-hyperswitch] ⚠ MainActivity.java not found — skipping.');
} else {
  patch('MainActivity.java', mainActivityPath, (content) => {
    if (content.includes('DefaultHardwareBackBtnHandler')) return content;

    // 1. Add import after the BridgeActivity import.
    content = content.replace(
      'import com.getcapacitor.BridgeActivity;',
      'import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler;\nimport com.getcapacitor.BridgeActivity;',
    );

    // 2. Add `implements DefaultHardwareBackBtnHandler` to the class declaration
    //    and inject the required override inside the class body.
    content = content.replace(
      /public class MainActivity extends BridgeActivity(\s*)\{([\s\S]*?)\}/,
      (match, space, body) => {
        const override = [
          '',
          '    @Override',
          '    public void invokeDefaultOnBackPressed() {',
          '        // Let Capacitor / the system handle the back press.',
          '        super.onBackPressed();',
          '    }',
          '',
        ].join('\n');

        // Avoid double blank line if body is already empty / just whitespace.
        const newBody = body.trimEnd().length === 0 ? override : body + override;
        return `public class MainActivity extends BridgeActivity implements DefaultHardwareBackBtnHandler${space}{${newBody}}`;
      },
    );

    return content;
  });
}

// ── Summary ───────────────────────────────────────────────────────────────────

if (patchCount > 0) {
  console.log(`[capacitor-hyperswitch] Android setup complete (${patchCount} file(s) patched).`);
} else {
  console.log('[capacitor-hyperswitch] Android setup complete (nothing to patch).');
}
