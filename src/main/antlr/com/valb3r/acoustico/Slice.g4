grammar Slice;

NUMBER: '-'?[0-9]+;
WHITESPACE: [ \r\n\t]+ -> skip;

start: slice_expression;

slice_expression
    : NUMBER                                                       # SliceNumber
    | '*'                                                          # SliceStar
    | left=slice_expression operator=':' right=slice_expression    # SliceRange
    ;