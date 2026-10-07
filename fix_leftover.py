import io

p = 'd:/skills exchange/app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt'
lines = io.open(p, encoding='utf-8').read().split('\n')
print('total lines:', len(lines))

start = None
for i, l in enumerate(lines):
    if l.startswith('        val from = JobStatus.fromValue') and 'unknown/legacy status: fail closed' in l:
        start = i
        break
print('start index:', start, '-> line', start + 1)

block_end = start + 20  # lines 1939..1958 inclusive
print('deleting:', repr(lines[start:block_end]))
io.open(p, 'w', encoding='utf-8').write('\n'.join(lines[:start] + lines[block_end:]))
print('done')
