[BITS 16]
[CPU 186]

[ORG 0]

begin:
mov ax, 0xB800
mov es, ax
mov ax, cs
mov ds, ax

mov dx, 40

mov cx, 10

xor si, si

top:
mov di, str

nxt_chr:

	mov al, [di]
	test al, al
	jz end
	inc di

	mov [es:si], al
	inc si
	mov byte [es:si], cl
	inc si
	inc cx
	jmp nxt_chr

end:

dec dx
test dx, dx
jnz top

hlt

str: db "Hello from 8086 DOS program!", 0

times (65536-16)-($-begin) db 0


reset:
jmp begin
times 16-($-reset) db 0
