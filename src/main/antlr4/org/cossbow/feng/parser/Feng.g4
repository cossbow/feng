// Feng Language Parser: missing summary ...

grammar Feng;

//*****************************//
//            Parser           //
//*****************************//




//
// headers
//
// import
source
    : import_* global* EOF
    ;
import_
    : IMPORT module (alias=Identifier)? SEMI
    ;
module
    : Identifier (DOLLAR Identifier)*
    ;
symbol
    : (mod=Identifier DOLLAR)? name=Identifier
    ;

exportable
    : EXPORT?
    ;



//
// global
//
global
    : def=typeDefinition            # GlobalTypeDefinition
    | def=functionDefinition        # GlobalFunctionDefinition
    | exportable declaration SEMI   # GlobalDeclaration
    | macro                         # GlobalMacro
    ;





//
// type definition
//
typeDefinition
    : structureDefinition
    | enumDefinition
    | classDefinition
    | interfaceDefinition
    | prototypeDefinition
    | attributeDefinition
    ;







//
// attribute
//
// attribute: defination
attributeDefinition
    : modifier ATTRIBUTE name=Identifier '{' attributeMember* '}'
    ;
attributeMember
    : attributeMemberField
    | attributeMemberArray
    ;
attributeMemberField
    : name=Identifier type=Identifier (ASSIGN value=expression)? SEMI
    ;
attributeMemberArray
    : name=Identifier '[' ']' type=Identifier (ASSIGN values=arrayExpr)? SEMI
    ;
// attribute: declaration
attribute
    : AT symbol ('(' init=objectExpr ')')?
    ;
attributes
    : attribute*
    ;




modifier
    : attributes exportable
    ;




//
// structure
//

// structure: struct or union
structureDefinition
    : modifier domain=(STRUCT | UNION) name=Identifier typeParameters? '{' structureFieldsDef* '}'
    ;
// structure unnamed
unnamedStructureDefinition
    : domain=(STRUCT | UNION) '{' structureFieldsDef* '}'
    ;
structureFieldsDef
    : fields=structureFields type=structureFieldType SEMI
    ;
structureFieldType
    : definedStructureFieldType
    | unnamedStructureFieldType
    | arrayStructureFieldType
    ;
arrayStructureFieldType
    : '[' len=expression ']' element=structureFieldType
    ;
structureFields
    : structureField (COMMA structureField)*
    ;
// structure field: bitfield must be constant expression
structureField
    : name=Identifier ('(' expression ')')?
    ;
definedStructureFieldType
    : definedType
    ;
// structure local define type
unnamedStructureFieldType
    : unnamedStructureDefinition
    ;




//
// interface
//
interfaceDefinition
    : modifier INTERFACE name=Identifier typeParameters? '{' interfaceMember* '}'
    ;
interfaceMember
    : method=interfaceMemberMethod
    | part=interfaceMemberPart
    | macro
    ;
interfaceMemberMethod
    : modifier name=Identifier typeParameters? prototype SEMI
    ;
interfaceMemberPart
    : definedType  SEMI
    ;


//
// class
//
classDefinition
    : modifier CLASS name=Identifier typeParameters? classExtension '{' classMember* '}'
    ;
classExtension
    : FINAL
    | classInherit? classImpl?
    ;
classMember
    : modifier classMemberImpl
    ;
classMemberImpl
    : fields=classMemberFields
    | method=classMemberMethod
    | macro
    ;
classMemberFields
    : declare=(VAR|CONST) identifierList typeDeclarer SEMI
    ;
classMemberMethod
    : def=funcDef
    ;
classInherit
    : COLON definedType
    ;
classImpl
    : '(' definedType (COMMA definedType)* ')'
    ;



//
// enum: enumeration type
//
enumDefinition
    : modifier ENUM name=Identifier '{' enumValue (COMMA enumValue)* COMMA? '}'
    ;
enumValue
    : name=Identifier (ASSIGN value=expression)?
    ;




//
// function
//
funcDef
    : FUNC name=Identifier typeParameters? (procedure|prototype SEMI)
    ;
functionDefinition
    : modifier def=funcDef
    ;
prototypeDefinition
    : modifier FUNC name=Identifier ASSIGN typeParameters? prototype SEMI
    ;




//
// procedure: for function & method
//
procedure
    : prototype blockStatement
    ;
prototype
    : '(' parametersSet? ')' returnSet?
    ;
parametersSet
    : parameters
    | typeDeclarerList
    ;
parameters
    : parameter (COMMA parameter)*
    ;
parameter
    : modifier identifierList typeDeclarer
    ;
returnSet
    : typeDeclarer
    | current=THIS
    ;


//
// macros
//
macro
    : modifier MACRO type=Identifier macroType        # MacroClass
    | modifier MACRO type=Identifier macroProcedure   # MacroFunc
    ;
macroType
    : name=Identifier '{' fields=macroVariables SEMI macroProcedure+ '}'
    ;
macroProcedure
    : name=Identifier '(' params=macroVariables? ')' typeDeclarer? '{' statementList expression? '}'
    ;
macroVariables
    : macroVariable (COMMA macroVariable)*
    ;
macroVariable
    : name=Identifier (type=typeDeclarer)?
    ;




// assignment
assignments
    : operands op=ASSIGN values=expressionList
    ;
operands
    : operand (COMMA operand)*
    ;
//
// assignable on left hand side
//
operand
    : symbol                # VariableOperand
    | primaryExpr indexOf   # IndexOperand
    | primaryExpr memberOf  # FieldOperand
    | MUL primaryExpr       # DereferOperand
    ;




// declaration
declaration
    : onlyDeclaration
    | assignedDeclaration
    ;
onlyDeclaration
    : declaredNames typeDeclarer
    ;
assignedDeclaration
    : declaredNames typeDeclarer? ASSIGN values=expressionList
    ;
declaredNames
    : attributes declare=(VAR|CONST) identifierList
    ;



//
// type declare
//

// declarer
typeDeclarer
    : primaryTypeDeclarer
    | arrayTypeDeclarer
    ;
arrayTypeDeclarer
    : '[' arrayType ']' typeDeclarer
    ;
arrayType
    : len=expression
    | refer
    ;
primaryTypeDeclarer
    : definedTypeDeclarer
    | funcTypeDeclarer
    ;
definedTypeDeclarer
    : refer? definedType
    ;
refer
    : kind=(MUL|BITAND) required=NOT? unmodifiable=HASH?
    ;
funcTypeDeclarer
    : required=NOT? FUNC prototype
    // 由于语法有冲突，仅匹配非空，可空的由definedTypeDeclarer代管
    | required=NOT definedType
    ;
// typeDeclarer list
typeDeclarerList
    : typeDeclarer (COMMA typeDeclarer)*
    ;


//
// generic
//

// generic: use
typeArguments
    : BACKTICK typeDeclarerList BACKTICK
    ;
definedType
    : symbol typeArguments?
    ;

// generic: for definer, with constraint expressions
typeParameters
    : BACKTICK typeParameter (COMMA typeParameter)* BACKTICK
    ;
typeParameter
    : name=Identifier typeConstraint?
    ;
typeConstraint
    : typeDomain                                    # DomainTypeConstraint
    | definedType                                   # DefinedTypeConstraint
    | l=typeConstraint op=BITAND r=typeConstraint   # BinaryTypeConstraint
    | l=typeConstraint op=BITOR r=typeConstraint    # BinaryTypeConstraint
    ;
typeDomain
    : CLASS | INTERFACE | ENUM | STRUCT | UNION | ATTRIBUTE | FUNC
    ;




//
// statement
//
statement
    : blockStatement
    | assignmentOperateStatement
    | assignmentsStatement
    | declarationStatement
    | callStatement
    | ifStatement
    | switchStatement
    | forStatement
    | throwStatement
    | tryStatement
    | returnStatement
    | continueStatement
    | breakStatement
    | gotoStatement
    | labeledStatement
    ;
// statement: block, usefull
blockStatement
    : '{' statementList '}'
    ;
statementList
    : statement*
    ;
// statement: call expression
callStatement
    : primaryExpr argumentSet SEMI
    ;
// statement: if
ifStatement
    : IF '(' (init=embedAssignment SEMI)? expression ')' yes=statement (ELSE not=statement)?
    ;
// statement: for
forStatement
    : FOR '(' expression ')' statement      # UnaryForStatement
    | FOR '(' forClause ')' statement       # TernaryForStatement
    | FOR '(' forIterator ')' statement     # IterableForStatement
    ;
forClause
    : init=embedAssignment SEMI expression SEMI next=embedAssignment
    ;
// statement: for: this's a sugar
forIterator
    : identifierList COLON expression
    ;
// statement: switch
switchStatement
    : SWITCH '(' (init=embedAssignment SEMI)? expression ')'
                '{' switchBranch* def=switchBranchDefault? '}'
    ;
switchBranch
    : CASE expressionList body=blockStatement
    ;
switchBranchDefault
    : DEFAULT body=blockStatement
    ;
// assignment in control statements
embedAssignment
    : assignments
    | assignedDeclaration
    | assignmentOperation
    ;
// statement: throw
throwStatement
    : THROW expression SEMI
    ;
// statement: try
tryStatement
    : tryPrefix catchClause     # TryWithCatchStatement
    | tryPrefix finalClause     # TryWithFinalStatement
    ;
tryPrefix
    : TRY blockStatement catchClause*
    ;
catchClause
    : CATCH '(' modifier name=Identifier? catchTypeSet ')' blockStatement
    ;
catchTypeSet
    : typeDeclarer (BITOR typeDeclarer)*
    ;
finalClause
    : FINAL blockStatement
    ;


assignmentOperation
    : operand assignmentOperator expression
    ;
assignmentOperator
    : op=( ASSIGN_AND
         | ASSIGN_OR
         | ASSIGN_ADD
         | ASSIGN_SUB
         | ASSIGN_MUL
         | ASSIGN_DIV
         | ASSIGN_MOD
         | ASSIGN_BITAND
         | ASSIGN_BITOR
         | ASSIGN_BITXOR
         | ASSIGN_LSHIFT
         | ASSIGN_RSHIFT )
    ;

assignmentOperateStatement
    : assignmentOperation SEMI
    ;
assignmentsStatement
    : assignments SEMI
    ;
declarationStatement
    : declaration SEMI
    ;



// statement: return
returnStatement
    : RETURN result=expression? SEMI
    ;
// statement: loop control
continueStatement
    : CONTINUE label=Identifier? SEMI
    ;
breakStatement
    : BREAK label=Identifier? SEMI
    ;
gotoStatement
    : GOTO label=Identifier SEMI
    ;
// statement: mark label to a statement
labeledStatement
    : label=Identifier COLON statement
    ;








//
// expresion
//
expression
    // right associativity expresion
    : rightAssocExpr                                        # RightAssocExpression_
    // operators start
    | lhs=expression op=(MUL|DIV|MOD) rhs=expression        # BinaryExpression
    | lhs=expression op=(ADD|SUB) rhs=expression            # BinaryExpression
    | lhs=expression op=(LSHIFT|RSHIFT) rhs=expression      # BinaryExpression
    | lhs=expression op=BITAND rhs=expression               # BinaryExpression
    | lhs=expression op=BITXOR rhs=expression               # BinaryExpression
    | lhs=expression op=BITOR rhs=expression                # BinaryExpression
    | lhs=expression op=(LT|LE|EQ|NE|GT|GE) rhs=expression  # BinaryExpression
    | lhs=expression op=AND rhs=expression                  # BinaryExpression
    | lhs=expression op=OR rhs=expression                   # BinaryExpression
    | c=expression QUESTION y=expression COLON n=expression # ConditionalExpression
    ;

rightAssocExpr
    : powerExpr                             # PowerExpression_
    | op=(ADD|SUB|NOT) rightAssocExpr       # UnaryExpression       // priority=2
    ;

powerExpr
    : dereferExpr                           # DereferExpression_
    | dereferExpr op=POW rightAssocExpr     # PowerExpression       // priority=1
    ;

dereferExpr
    : primaryExpr       # PrimaryExpression
    | MUL primaryExpr   # DereferExpression
    ;

primaryExpr
    : operandExpr                                       # OperandExpression_
    | primaryExpr assert                                # AssertExpression
    | primaryExpr indexOf                               # IndexOfExpression
    | primaryExpr memberOf typeArguments?               # MemberOfExpression
    | primaryExpr argumentSet                           # CallExpression
    ;

operandExpr
    : literal                   # LiteralExpression
    | objectExpr                # ObjectExpression
    | arrayExpr                 # ArrayExpression
    | pairsExpr                 # PairsExpression
    | symbol typeArguments?     # SymbolExpression
    | current=(THIS|SUPER)      # CurrentExpression
    | FUNC procedure            # LambdaExpression
    | '(' expression ')'        # ParenExpression
    | new                       # NewExpression
    | blockExpr                 # BlockExpression
    | sizeof                    # SizeofExpression
    ;


argumentSet
    : '(' args=expressionList? COMMA? ')'
    ;

// closure
blockExpr
    : '{' statementList expression '}'
    ;


expressionList
    : expression (COMMA expression)*
    ;






// init class & struct & union
objectExpr
    : definedType? '{' (objectEntry (COMMA objectEntry)* COMMA?)? '}'
    ;
objectEntry
    : name=Identifier ASSIGN value=expression
    ;
// init array
arrayExpr
    : ('[' len=expression? ']' et=typeDeclarer)? '[' elements=expressionList? COMMA? ']'
    ;
// init with key-value pair
pairsExpr
    : '{' pair (COMMA pair)* '}'
    ;
pair
    : key=expression COLON value=expression
    ;





// index: default for array
indexOf
    : '[' expression ']'
    ;

memberOf
    : DOT member=Identifier
    ;


// type for new
new
    : NEW '(' newType (COMMA expression)? ')'
    ;
assert
    : QUESTION '(' typeDeclarer ')'
    ;
newType
    : definedType
    | newArrayType
    ;
newArrayType
    : '[' len=expression ']' typeDeclarer
    ;
sizeof
    : SIZEOF '(' typeDeclarer ')'
    ;



//
// literal
//
literal
    : integerLiteral
    | FloatLiteral
    | StringLiteral
    | BoolLiteral
    | NilLiteral
    ;


// identifier series, for many
identifierList
    : Identifier (COMMA Identifier)*
    ;









//*****************************//
//             Lexer           //
//*****************************//

//
// Literals
//

// String
StringLiteral                       : '"' StringCharacters? '"' ;
fragment StringCharacters           : StringCharacter+ ;
fragment StringCharacter            : ~["\\\r\n] | EscapeSequence;
fragment EscapeSequence
    : '\\' [btnfr"'\\]
    | OctalEscape
    | UnicodeEscape // This is not in the spec but prevents having to preprocess the input
    ;
fragment OctalEscape
    : '\\' OctalDigit
    | '\\' OctalDigit OctalDigit
    | '\\' ZeroToThree OctalDigit OctalDigit
    ;
fragment ZeroToThree    : [0-3];
fragment UnicodeEscape  : '\\' 'u'+ HexDigit HexDigit HexDigit HexDigit;

// Float
FloatLiteral
    : Digits DOT Digits? ExponentPart?
    | Digits ExponentPart
    | Digits DOT
    | DOT Digits
    ;
fragment ExponentPart               : [eE] [+-]? Digits ;

 // Integer
integerLiteral
    : DecimalInteger
    | HexInteger
    | OctalInteger
    | BinaryInteger
    ;
// Integer: Number
DecimalInteger      : '0' | NonZeroDigit Digit* ;
HexInteger          : '0' [xX] HexDigit+ ;
OctalInteger        : '0' [oO] OctalDigit+ ;
BinaryInteger       : '0' [bB] [01]+ ;
// Integer: Digit
fragment Digits                     : Digit+ ;
fragment Digit                      : '0' | NonZeroDigit ;
fragment NonZeroDigit               : [1-9] ;
fragment OctalDigit                 : [0-7] ;
fragment HexNumeral                 : '0' [xX] HexDigit+ ;
fragment HexDigit                   : [0-9a-fA-F] ;

// Bool
BoolLiteral                         : 'true' | 'false';

// nil: means zero or empty
NilLiteral                          : 'nil';




//
// Keywords
//
// name of this language
FENG1            : 'Feng' ;
FENG2            : 'feng' ;
FENG3            : 'FENG' ;
// Keywords: export & import
EXPORT          : 'export' ;
IMPORT          : 'import' ;
// Keywords: Type & Declare
STRUCT          : 'struct' ;
UNION           : 'union' ;
ENUM            : 'enum' ;
ATTRIBUTE       : 'attribute' ;
INTERFACE       : 'interface' ;
CLASS           : 'class' ;
FUNC            : 'func' ;
MACRO           : 'macro' ;
CONST           : 'const' ;
VAR             : 'var' ;
NEW             : 'new' ;
SIZEOF          : 'sizeof' ;
// Keywords: Control
RETURN          : 'return' ;
IF              : 'if' ;
ELSE            : 'else' ;
FOR             : 'for' ;
CONTINUE        : 'continue' ;
BREAK           : 'break' ;
SWITCH          : 'switch' ;
CASE            : 'case' ;
DEFAULT         : 'default' ;
GOTO            : 'goto' ;
THROW           : 'throw' ;
TRY             : 'try' ;
CATCH           : 'catch' ;
FINAL           : 'final' ;
// Class
THIS            : 'this' ;
SUPER           : 'super' ;
//
// Separators
//
PAREN_L         : '(' ;
PAREN_R         : ')' ;
BRACE_L         : '{' ;
BRACE_R         : '}' ;
BRACK_L         : '[' ;
BRACK_R         : ']' ;
SEMI            : ';' ;
COMMA           : ',' ;
DOT             : '.' ;
COLON           : ':' ;
QUESTION        : '?' ;
AT              : '@' ;
HASH            : '#' ;
DOLLAR          : '$' ;
BACKTICK        : '`' ;
BACKSLASH       : '\\' ;

//
// Operators
//
ASSIGN              : '=' ;
COPY                : ':=' ;
ARROW_L             : '<-' ;
ARROW_R             : '->' ;
// combines
ASSIGN_AND          : '&&=' ;
ASSIGN_OR           : '||=' ;
ASSIGN_ADD          : '+=' ;
ASSIGN_SUB          : '-=' ;
ASSIGN_MUL          : '*=' ;
ASSIGN_DIV          : '/=' ;
ASSIGN_MOD          : '%=' ;
ASSIGN_BITAND       : '&=' ;
ASSIGN_BITOR        : '|=' ;
ASSIGN_BITXOR       : '~=' ;
ASSIGN_LSHIFT       : '<<=' ;
ASSIGN_RSHIFT       : '>>=' ;
//
// Relational
GT          : '>' ;
LT          : '<' ;
EQ          : '==' ;
NE          : '!=' ;
LE          : '<=' ;
GE          : '>=' ;
// Bool
NOT         : '!' ;
AND         : '&&' ;
OR          : '||' ;
// arithmetic
ADD         : '+' ;
SUB         : '-' ;
MUL         : '*' ;
DIV         : '/' ;
MOD         : '%' ;
POW         : '^' ;
// bit
BITAND      : '&' ;
BITOR       : '|' ;
BITXOR      : '~' ;
LSHIFT      : '<<' ;
RSHIFT      : '>>' ;




// Identifier
Identifier: IdentifierStart (IdentifierStart | Digit)*;
fragment IdentifierStart    : [a-zA-Z_] ;




//*****************************//
//   Whitespace and comments   //
//*****************************//

WS: [ \t\r\n\u000C]+ -> skip;

COMMENT: '/*' .*? '*/' -> channel(HIDDEN);

LINE_COMMENT: '//' ~[\r\n]* -> channel(HIDDEN);
