#!/usr/bin/env python3
"""Exercise the production Lua scripts against a disposable local Redis, not an application DB.
Run: python3 src/test/redis/enrollment_generation_test.py PORT
"""
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys
import uuid

port = sys.argv[1]
cli = os.environ.get('REDIS_CLI', 'redis-cli')
root = Path(__file__).resolve().parents[2] / 'main' / 'resources' / 'redis'
key = 'test:enrollment:{' + str(uuid.uuid4()) + '}'

def command(*args):
    result = subprocess.run([cli, '-h', '127.0.0.1', '-p', port, '--raw', *map(str, args)],
                            check=True, capture_output=True, text=True)
    if result.stdout.startswith('ERR '):
        raise AssertionError(result.stdout)
    return result.stdout.strip()

def run(name, *args):
    return command('--eval', root / ('enrollment-' + name + '.lua'), key, ',', *args)

def payload(generation):
    return json.dumps({'generation': generation, 'state': 'READY', 'secretCipher': 'test-only',
                       'keyVersion': 1, 'periodSeconds': 30})

try:
    a, b = str(uuid.uuid4()), str(uuid.uuid4())
    assert run('start', payload(a), 300) == '1'
    assert run('start', payload(b), 300) == '0'
    assert run('cancel', a) == '1'
    assert run('start', payload(b), 300) == '1'
    assert run('cancel', a) == '0'
    assert json.loads(command('GET', key))['generation'] == b
    assert run('claim', a, 'wrong', '30') == ''
    assert run('claim', b, 'owner', '45') == ''
    assert json.loads(run('claim', b, 'owner', '30'))['generation'] == b
    assert command('PTTL', key) == '-1'
    assert run('cancel', b) == '-1'
    assert run('start', payload(a), 300) == '0'
    assert run('finish', b, 'wrong') == '0'
    assert run('finish', b, 'owner') == '1'
    assert run('cancel', b) == '0'

    # Start cannot overwrite a generation regardless of whether claim happens first.
    for _ in range(50):
        a, b = str(uuid.uuid4()), str(uuid.uuid4())
        assert run('start', payload(a), 300) == '1'
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            f1 = pool.submit(run, 'claim', a, 'owner', '30')
            f2 = pool.submit(run, 'start', payload(b), 300)
            claimed, started = f1.result(), f2.result()
        current = json.loads(command('GET', key))
        if claimed:
            assert json.loads(claimed)['generation'] == a and started == '0'
            assert current['generation'] == a and current['state'] == 'CLAIMED'
            assert run('finish', a, 'owner') == '1'
        else:
            raise AssertionError("a READY generation must be claimed exactly once")
    # A concurrent cancellation and claim cannot both succeed.
    for _ in range(50):
        generation = str(uuid.uuid4())
        assert run('start', payload(generation), 300) == '1'
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            f1 = pool.submit(run, 'claim', generation, 'owner', '30')
            f2 = pool.submit(run, 'cancel', generation)
            claimed, cancelled = f1.result(), f2.result()
        if claimed:
            assert cancelled == '-1'
            assert run('finish', generation, 'owner') == '1'
        else:
            assert cancelled == '1' and command('EXISTS', key) == '0'
    command('SET', key, 'not-json')
    assert run('start', payload(a), 300) == '-1'
    print('Enrollment generation Lua tests passed (including 100 concurrent races).')
finally:
    command('DEL', key)
