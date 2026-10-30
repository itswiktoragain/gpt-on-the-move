#!/usr/bin/env python3
"""Apply the checksum-verified source diff after the existing Mobile 1.2 overlay.
The decoded, reviewable diff is included in the build artifact and source download.
"""
import base64,hashlib,lzma,pathlib,subprocess,sys
here=pathlib.Path(__file__).resolve().parent
encoded=''.join((here/('patch.%d.b64'%i)).read_text().strip() for i in range(3))
data=base64.b64decode(encoded,validate=True)
if hashlib.sha256(data).hexdigest()!='22bea97ade7d26360ed1e7028125daa6354b98f7514c1f660f441e014f463539':
 raise RuntimeError('Keyboard/icon source patch checksum mismatch')
patch=lzma.decompress(data)
(here/'keyboard-icons.patch').write_bytes(patch)
root=pathlib.Path(sys.argv[1]).resolve()
subprocess.run(['git','apply','--check','-'],input=patch,cwd=root,check=True)
subprocess.run(['git','apply','-'],input=patch,cwd=root,check=True)
# Intermediary aliases are also constrained by class and method signature.
p=root/'jogger_touch_agent/src/main/java/com/minecraftjogger/touch/TextInputProbe.java'
s=p.read_text().replace('"EditBox","TextFieldWidget","GuiTextField","TextField"','"EditBox","TextFieldWidget","GuiTextField","TextField","net.minecraft.class_342"')
s=s.replace('"getFocused","getFocusedChild","getFocusedWidget"','"getFocused","getFocusedChild","getFocusedWidget","method_25399"')
s=s.replace('"isFocused","isTextFieldFocused"','"isFocused","isTextFieldFocused","method_25370"')
p.write_text(s)
print('Applied Mobile 1.3: themed in-game keyboard, automatic focus, custom/grass icons and shortcut refresh.')
