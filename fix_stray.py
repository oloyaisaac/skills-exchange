import io

p = 'd:/skills exchange/app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt'
lines = io.open(p, encoding='utf-8').read().split('\n')
print('total lines:', len(lines))

anchor = None
for i, l in enumerate(lines):
    if l.strip() == 'if (!isOwner) return false':
        anchor = i
        break
print('anchor index:', anchor, '-> line', anchor + 1)
print('lines around anchor:')
for j in range(anchor - 1, anchor + 9):
    print(j, repr(lines[j]))

# delete the two stray closers and the blank line after them:
#   1915 '        }', 1916 '    }', 1917 ''
del lines[anchor + 2 : anchor + 5]

io.open(p, 'w', encoding='utf-8').write('\n'.join(lines))
print('fixed, total lines now:', len(lines))
