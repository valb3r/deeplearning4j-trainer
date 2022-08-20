grammar Calculator;
import Slice;

POW: '^';
MUL: '*';
DIV: '/';
ADD: '+';
SUB: '-';
NUMBER: '-'?[0-9]+;
SD_VAR: '`' (~'"' | '\\"')* '`';
WHITESPACE: [ \r\n\t]+ -> skip;

start : expression;

expression
   : NUMBER                                               # Number
   | SD_VAR                                               # SdRef
   | '(' inner=expression ')'                             # Parentheses
   | ref=expression '[' index=slice_expression ']'        # Slices
   | left=expression operator=POW right=expression        # Power
   | left=expression operator=(MUL|DIV) right=expression  # MultiplicationOrDivision
   | left=expression operator=(ADD|SUB) right=expression  # AdditionOrSubtraction
   ;