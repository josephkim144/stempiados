#!/bin/bash
nasm -felf main.asm -o main.asm.o
gcc -m32 main.c main.asm.o -o main
nasm -fbin data.asm -o data.o
