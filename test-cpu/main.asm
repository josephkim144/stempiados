; Assembly program that actually does the operation
[BITS 32]

extern flags
extern in_flags

section .text

%macro opz 4
global %1
global %2
%1:
	push dword [in_flags]
	popf
	mov al, [esp + 4]
	mov cl, [esp + 8]
	%3 al, cl
	pushf
	pop dword [flags]
	ret
%2:
	push dword [in_flags]
	popf
	mov ax, [esp + 4]
	mov cx, [esp + 8]
	%3 ax, %4
	pushf
	pop dword [flags]
	ret
%endmacro

opz test_add8, test_add16, add, cx
opz test_or8, test_or16, or, cx
opz test_adc8, test_adc16, adc, cx
opz test_sbb8, test_sbb16, sbb, cx
opz test_and8, test_and16, and, cx
opz test_sub8, test_sub16, sub, cx
opz test_xor8, test_xor16, xor, cx
opz test_cmp8, test_cmp16, cmp, cx
opz test_test8, test_test16, test, cx
opz test_ror8, test_ror16, ror, cl
opz test_rcr8, test_rcr16, rcr, cl
opz test_rol8, test_rol16, rol, cl
opz test_rcl8, test_rcl16, rcl, cl
opz test_shl8, test_shl16, shl, cl
opz test_sar8, test_sar16, sar, cl
opz test_shr8, test_shr16, shr, cl
