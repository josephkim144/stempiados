// 8086 CPU testing utility. Can only be run on a 32-bit x86 CPU
// By: jkim13

#include <stdio.h>
#include <stdlib.h>
#include <stddef.h>
#include <stdint.h>

#define CF (1 << 0)
#define PF (1 << 2)
#define AF (1 << 4)
#define ZF (1 << 6)
#define SF (1 << 7)
#define DF (1 << 10)
#define OF (1 << 11)
#define OSZAPC (OF | SF | ZF | AF | PF | CF)

int32_t flags; // Written to by pushf/popf
int32_t in_flags; // Written in by pushf/popf

static const int testingpairs[] = {
	0, // Extra 0 to keep Valgrind happy
	0x0000, 0x0001, 0x0002, 0x0003, 0x0004, 0x0005, 0x0006, 0x0007,
	0x0008, 0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x000E, 0x000F,
	0x3FF0, 0x3FF1, 0x3FF2, 0x3FF3, 0x3FF4, 0x3FF5, 0x3FF6, 0x3FF7,
	0x3FF8, 0x3FF9, 0x3FFA, 0x3FFB, 0x3FFC, 0x3FFD, 0x3FFE, 0x3FFF,
	0x4000, 0x4001, 0x4002, 0x4003, 0x4004, 0x4005, 0x4006, 0x4007,
	0x4008, 0x4009, 0x400A, 0x400B, 0x400C, 0x400D, 0x400E, 0x400F,
	0x7FF0, 0x7FF1, 0x7FF2, 0x7FF3, 0x7FF4, 0x7FF5, 0x7FF6, 0x7FF7,
	0x7FF8, 0x7FF9, 0x7FFA, 0x7FFB, 0x7FFC, 0x7FFD, 0x7FFE, 0x7FFF,
	0x8000, 0x8001, 0x8002, 0x8003, 0x8004, 0x8005, 0x8006, 0x8007,
	0x8008, 0x8009, 0x800A, 0x800B, 0x800C, 0x800D, 0x800E, 0x800F,
	0xBFF0, 0xBFF1, 0xBFF2, 0xBFF3, 0xBFF4, 0xBFF5, 0xBFF6, 0xBFF7,
	0xBFF8, 0xBFF9, 0xBFFA, 0xBFFB, 0xBFFC, 0xBFFD, 0xBFFE, 0xBFFF,
	0xC000, 0xC001, 0xC002, 0xC003, 0xC004, 0xC005, 0xC006, 0xC007,
	0xC008, 0xC009, 0xC00A, 0xC00B, 0xC00C, 0xC00D, 0xC00E, 0xC00F,
	0xFFF0, 0xFFF1, 0xFFF2, 0xFFF3, 0xFFF4, 0xFFF5, 0xFFF6, 0xFFF7,
	0xFFF8, 0xFFF9, 0xFFFA, 0xFFFB, 0xFFFC, 0xFFFD, 0xFFFE, 0xFFFF
};

void print_result(char* c, int op1, int op2, int result) {
	printf("%s: %04x %04x %04x %04x %04x\n", c, op1, op2, in_flags, result, flags);
	//      c   op1  op2  infl res  rflag
}

#define DTEST(x) \
	flags=0;print_result(#x "16", op1, op2, test_##x##16(op1, op2));\
	print_result(#x "8", op1, op2, test_##x##8(op1, op2));\
	flags=OSZAPC;print_result(#x "16", op1, op2, test_##x##16(op1, op2));\
	print_result(#x "8", op1, op2, test_##x##8(op1, op2));
#define DTEST1(x) \
	flags=0;\
	print_result(#x "8", op1, op2, test_##x(op1, op2));\
	flags=OSZAPC;\
	print_result(#x "8", op1, op2, test_##x(op1, op2));

#define AOP(x) extern int test_##x##8(int op1, int op2);extern int test_##x##16(int op1, int op2);
#define AOP1(x) extern int test_##x(int op1, int op2);

		AOP(add);
		AOP(or);
		AOP(adc);
		AOP(sbb);
		AOP(and);
		AOP(sub);
		AOP(xor);
		AOP(cmp);
		AOP(test);
		AOP(ror);
		AOP(rcr);
		AOP(rol);
		AOP(rcl);
		AOP(shr);
		AOP(sar);
		AOP(shl);
		AOP1(das);
		AOP1(daa);
		AOP1(aaa);
		AOP1(aam);
		AOP1(aad);
void test_arith() {
	for(int i=1;i<sizeof(testingpairs)/sizeof(int);i++){
		int op1 = testingpairs[i-1];
		int op2 = testingpairs[i];
		DTEST(add);
		DTEST(or);
		DTEST(adc);
		DTEST(sbb);
		DTEST(and);
		DTEST(sub);
		DTEST(xor);
		DTEST(cmp);
		DTEST(test);
		DTEST(ror);
		DTEST(rcr);
		DTEST(rol);
		DTEST(rcl);
		DTEST(shr);
		DTEST(sar);
		DTEST(shl);
		DTEST1(das);
		DTEST1(daa);
		DTEST1(aaa);
		DTEST1(aam);
		DTEST1(aad);
	}
}

int main() {
	
	test_arith();
}
