import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, unlinkSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

test('deployment exports only website, skips unchanged content, and preserves branch history', t => {
  const directory = mkdtempSync(join(tmpdir(), 'hycopy-deploy-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const repository = join(directory, 'source');
  const remote = join(directory, 'remote.git');
  execFileSync('git', ['init', '--bare', '--quiet', remote]);
  execFileSync('git', ['init', '--quiet', '--initial-branch=main', repository]);
  const git = (...args) => execFileSync('git', ['-C', repository, ...args], { encoding: 'utf8' }).trim();
  const remoteGit = (...args) => execFileSync('git', ['--git-dir', remote, ...args], { encoding: 'utf8' }).trim();
  git('config', 'user.name', 'Website deployment test');
  git('config', 'user.email', 'test@example.invalid');
  git('remote', 'add', 'origin', remote);
  mkdirSync(join(repository, 'website'));
  for (const [name, content] of Object.entries({
    'package.json': '{"private":true}',
    'package-lock.json': '{}',
    Procfile: 'web: node server.mjs\n',
    'server.mjs': '// Website\n',
  })) writeFileSync(join(repository, 'website', name), content);
  writeFileSync(join(repository, 'minecraft.txt'), 'Parent project');
  const commit = () => { git('add', '.'); git('commit', '--quiet', '-m', 'Update fixture'); return git('rev-parse', 'HEAD'); };
  const script = fileURLToPath(new URL('../scripts/deploy.sh', import.meta.url));
  const publish = source => execFileSync('bash', [script], {
    cwd: repository,
    env: { ...process.env, SOURCE_COMMIT: source, DEPLOYMENT_BRANCH: 'website', DEPLOYMENT_REMOTE: 'origin' },
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });

  const firstSource = commit();
  publish(firstSource);
  const firstDeployment = remoteGit('rev-parse', 'refs/heads/website');
  assert.equal(remoteGit('rev-parse', 'website^{tree}'), git('rev-parse', 'HEAD:website'));
  assert.deepEqual(remoteGit('ls-tree', '--name-only', 'website').split('\n'), ['Procfile', 'package-lock.json', 'package.json', 'server.mjs']);

  writeFileSync(join(repository, 'minecraft.txt'), 'Parent-only change');
  assert.match(publish(commit()), /already current/);
  assert.equal(remoteGit('rev-parse', 'website'), firstDeployment);

  writeFileSync(join(repository, 'website', 'server.mjs'), '// Updated website\n');
  publish(commit());
  const secondDeployment = remoteGit('rev-parse', 'website');
  assert.notEqual(secondDeployment, firstDeployment);
  assert.equal(remoteGit('rev-parse', 'website^'), firstDeployment);
  assert.equal(remoteGit('rev-parse', 'website^{tree}'), git('rev-parse', 'HEAD:website'));

  unlinkSync(join(repository, 'website', 'package.json'));
  const invalidSource = commit();
  const failed = spawnSync('bash', [script], {
    cwd: repository,
    env: { ...process.env, SOURCE_COMMIT: invalidSource, DEPLOYMENT_BRANCH: 'website', DEPLOYMENT_REMOTE: 'origin' },
  });
  assert.notEqual(failed.status, 0);
  assert.equal(remoteGit('rev-parse', 'website'), secondDeployment);
});
