/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * @flow strict-local
 * @format
 */

const {exec} = require('shelljs');
const path = require('path');
const fs = require('fs');

/*::
type ManifestEntry = {
  name: string,
  version: string,
  tarball: string,
  tags: Array<string>,
  access: ?('public' | 'restricted'),
};
*/

function publishFromTarballs(tarballsDir /*: string */) {
  const manifestPath = path.join(tarballsDir, 'manifest.json');

  if (!fs.existsSync(manifestPath)) {
    throw new Error(`manifest.json not found at ${manifestPath}`);
  }

  const manifest /*: Array<ManifestEntry> */ = JSON.parse(
    fs.readFileSync(manifestPath, 'utf8'),
  );

  console.log(
    `Publishing ${manifest.length} package(s) from ${tarballsDir}`,
  );

  const failedPackages = [];

  for (const entry of manifest) {
    const tarballPath = path.join(tarballsDir, entry.tarball);
    console.log(`- Publishing ${entry.name}@${entry.version}`);

    try {
      runPublish(entry.name, tarballPath, entry.tags, entry.access);
    } catch {
      console.log('--- Retrying once! ---');
      try {
        runPublish(entry.name, tarballPath, entry.tags, entry.access);
      } catch (e) {
        failedPackages.push(entry.name);
      }
    }
  }

  if (failedPackages.length) {
    throw new Error(
      `Failed to publish ${failedPackages.length} package(s): ${failedPackages.join(', ')}`,
    );
  }

  console.log('Done ✅');
}

function runPublish(
  packageName /*: string */,
  tarballPath /*: string */,
  tags /*: Array<string> */,
  access /*: ?string */,
) {
  let tagsFlag = '';
  if (tags != null) {
    tagsFlag = tags.includes('--no-tag')
      ? ' --no-tag'
      : tags
          .filter(Boolean)
          .map(t => ` --tag ${t}`)
          .join('');
  }

  const accessFlag = access != null ? ` --access ${access}` : '';
  const result = exec(
    `npm publish ${tarballPath} --provenance${tagsFlag}${accessFlag}`,
  );

  if (result.code !== 0) {
    console.error(
      `Failed to publish ${packageName}. npm publish exited with code ${result.code}:`,
    );
    console.error(result.stderr);
    throw new Error(result.stderr);
  }

  console.log(`Published ${packageName} to npm`);
}

function main() {
  const tarballsDir = path.resolve(process.argv[2] || './npm-tarballs');
  publishFromTarballs(tarballsDir);
}

if (require.main === module) {
  main();
}

module.exports = {publishFromTarballs};
