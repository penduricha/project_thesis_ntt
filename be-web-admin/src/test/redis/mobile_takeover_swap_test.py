#!/usr/bin/env python3
"""Test the experimental Lua primitive only, using a disposable local Redis.
Run: REDIS_CLI=/path/to/redis-cli python3 src/test/redis/mobile_takeover_swap_test.py PORT
This does not test MFA, client proof, confirmation, tokens, or a Spring endpoint.
"""
import concurrent.futures
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import uuid

cli, port = os.environ.get('REDIS_CLI', 'redis-cli'), sys.argv[1]
script = Path(__file__).resolve().parents[2] / 'main/resources/redis/mobile-takeover-swap.lua'
admin = str(secrets.randbelow(10**14 - 1) + 1)
tag = '{' + admin + '}'
slot, info = 'auth:mobile:' + tag, 'auth:mobile-info:' + tag
created = {slot, info}

def command(*args):
    result = subprocess.run([cli, '-h', '127.0.0.1', '-p', port, '--raw', *map(str, args)],
                            check=True, capture_output=True, text=True)
    if result.stdout.startswith('ERR '):
        raise AssertionError(result.stdout)
    return result.stdout.strip()

def session(sid):
    key = 'auth:session:' + tag + ':' + sid
    created.add(key)
    return key

def challenge(old, new, proof, expiry=True):
    key = 'auth:takeover:' + tag + ':' + secrets.token_hex(32)
    created.add(key)
    payload = json.dumps({'oldSessionId': old, 'newSessionId': new,
                          'proofHash': proof, 'info': '{"deviceName":"candidate"}'})
    command('SET', key, payload, *(('EX', '120') if expiry else ()))
    return key

def swap(key, old, new, proof):
    return command('--eval', script, key, session(old), session(new), slot, info,
                   ',', admin, old, new, proof, 3600)

try:
    old, a, b = str(uuid.uuid4()), str(uuid.uuid4()), str(uuid.uuid4())
    proof = secrets.token_hex(32)
    command('SET', session(old), 'MOBILE', 'EX', 3600)
    command('SET', slot, old, 'EX', 3600)
    key = challenge(old, a, proof, expiry=False)
    assert swap(key, old, a, proof) == '0', 'a challenge without expiry must fail closed'
    command('DEL', key)
    key = challenge(old, a, proof)
    assert swap(key, old, a, secrets.token_hex(32)) == '0'
    assert command('GET', slot) == old
    command('SET', slot, b, 'EX', 3600)
    assert swap(key, old, a, proof) == '0', 'a stale observed slot must never be revoked'
    command('SET', slot, old, 'EX', 3600)
    command('SET', session(a), 'MOBILE', 'EX', 3600)
    assert swap(key, old, a, proof) == '0', 'must not reuse an existing session id'
    command('DEL', session(a))
    command('DEL', slot)
    assert swap(key, old, a, proof) == '0', 'concurrent logout invalidates the challenge'
    command('SET', slot, old, 'EX', 3600)
    assert swap(key, old, a, proof) == '1'
    assert command('GET', slot) == a and command('GET', session(a)) == 'MOBILE'
    assert command('EXISTS', session(old)) == '0'
    assert json.loads(command('GET', info))['deviceName'] == 'candidate'
    assert swap(key, old, a, proof) == '0', 'a consumed challenge is not reusable'

    # Two independently issued challenges for the same old slot must not both win.
    for _ in range(50):
        old, a, b = str(uuid.uuid4()), str(uuid.uuid4()), str(uuid.uuid4())
        p1, p2 = secrets.token_hex(32), secrets.token_hex(32)
        command('SET', session(old), 'MOBILE', 'EX', 3600)
        command('SET', slot, old, 'EX', 3600)
        k1, k2 = challenge(old, a, p1), challenge(old, b, p2)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            f1 = pool.submit(swap, k1, old, a, p1)
            f2 = pool.submit(swap, k2, old, b, p2)
            r1, r2 = f1.result(), f2.result()
        assert sorted([r1, r2]) == ['0', '1']
        winner, loser = (a, b) if r1 == '1' else (b, a)
        assert command('GET', slot) == winner
        assert command('GET', session(winner)) == 'MOBILE'
        assert command('EXISTS', session(old), session(loser)) == '0'
    print('Mobile takeover Lua primitive: stale, replay, expiry, proof and 50 concurrent swaps passed.')
finally:
    if created:
        command('DEL', *created)
