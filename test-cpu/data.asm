; DOS assembly program that contains all operations. For CPU.java
; By: jkim13

[CPU 186]
[BITS 16]

%define pad align 32,db 0x90

%macro opz 2
%1 al, cl
pad
%1 ax, %2
pad
%endmacro

%macro opa 1
%1
pad
%endmacro

opz add, cx
opz or, cx
opz adc, cx
opz sbb, cx
opz and, cx
opz sub, cx
opz xor, cx
opz cmp, cx
opz ror, cl
opz rcr, cl
opz rol, cl
opz rcl, cl
opz shl, cl
opz sar, cl
opz shr, cl

opa das
opa daa
opa aaa
opa aam
opa aad
