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
    : IMPORT module (alias=Identifier | flat=MUL)? SEMI
    ;
module
    : Identifier (DOT Identifier)*
    ;
symbol
    : (mod=Identifier HASH)? name=Identifier
    ;

exportable
    : EXPORT?
    ;



//
// global
//
global
    : exportable def=typeDefinition         # GlobalTypeDefinition
    | exportable def=functionDefinition     # GlobalFunctionDefinition
    | exportable declaration SEMI           # GlobalDeclaration
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
    : AT type=Identifier ('(' init=objectExpr ')')?
    ;
attributes
    : attribute*
    ;




modifier
    : attributes
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
    : arrayDeclarer elementType=structureFieldType
    ;
structureFields
    : structureField (COMMA structureField)*
    ;
// structure field: bitfield must be constant expression
structureField
    : name=Identifier (COLON expression)?
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
    : modifier CLASS name=Identifier typeParameters? classInherit? classImpl? '{' classMember* '}'
    ;
classMember
    : modifier exportable classMemberImpl
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
    : FUNC name=Identifier typeParameters? procedure
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
    : modifier ENUM name=Identifier '{' enumValue+ '}'
    ;
enumValue
    : name=Identifier (ASSIGN value=expression)? COMMA
    ;




//
// function
//
functionDefinition
    : modifier FUNC name=Identifier typeParameters? procedure
    ;
prototypeDefinition
    : modifier FUNC name=Identifier typeParameters? prototype SEMI
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
    | '(' typeDeclarerList ')'
    ;


//
// macros
//
macro
    : MACRO type=Identifier macroType        # MacroClass
    | MACRO type=Identifier macroProcedure   # MacroFunc
    ;
macroType
    : name=Identifier '{' fields=macroVariables SEMI macroProcedure+ '}'
    ;
macroProcedure
    : name=Identifier '(' params=macroVariables? ')' '{' statementList expression? '}'
    ;
macroVariables
    : macroVariable (COMMA macroVariable)*
    ;
macroVariable
    : name=Identifier (type=typeDeclarer)?
    ;




// assignment
assignments
    : operands=assignableOperands op=(ASSIGN|COPY) tuple
    ;
assignableOperands
    : assignableOperand (COMMA assignableOperand)*
    ;
//
// assignable on left hand side
//
assignableOperand
    : symbol                # VariableAssignableOperand
    | primaryExpr indexOf   # IndexAssignableOperand
    | primaryExpr memberOf  # MemberAssignableOperand
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
    : declaredNames typeDeclarer? ASSIGN tuple
    ;
declaredNames
    : modifier declare=(LET|VAR|CONST) identifierList
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
    : arrayDeclarer typeDeclarer
    ;
primaryTypeDeclarer
    : definedTypeDeclarer
    | funcTypeDeclarer
    ;
definedTypeDeclarer
    : pointerType? definedType
    ;
pointerType
    : kind=(MUL|BITAND)
    ;
funcTypeDeclarer
    : FUNC prototype
    ;
// typeDeclarer list
typeDeclarerList
    : typeDeclarer (COMMA typeDeclarer)*
    ;


//
// generic
//

// generic: reference
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
    : typeDomains                                   # DomainTypeConstraint
    | definedType                                   # DefinedTypeConstraint
    | l=typeConstraint op=BITAND r=typeConstraint   # BinaryTypeConstraint
    | l=typeConstraint op=BITOR r=typeConstraint    # BinaryTypeConstraint
    ;
typeDomains
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
    | localDefineStatement
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
ifTuple
    : IF '(' condition=expression ')' yes=tuple ELSE not=tuple
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
    : CASE expressionList COLON statementList (FALLTHROUGH SEMI)?
    ;
switchBranchDefault
    : DEFAULT COLON statementList
    ;
// mult-expression
switchTuple
    : SWITCH '(' expression ')' '{' switchRule* switchRuleDefault '}'
    ;
switchRule
    : CASE constants=expressionList COLON values=tuple SEMI
    ;
switchRuleDefault
    : DEFAULT COLON tuple SEMI
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
    : tryPrefix catchClause      # TryWithCatchStatement
    | tryPrefix finallyClause    # TryWithFinallyStatement
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
finallyClause
    : FINALLY blockStatement
    ;


assignmentOperation
    : assignableOperand assignmentOperator expression
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


localDefineStatement
    : localDefinition
    ;
localDefinition
    : typeDefinition
    | functionDefinition
    ;


// statement: return
returnStatement
    : RETURN result=tuple? SEMI
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


// tuple
tuple
    : arrayTuple
    | ifTuple
    | switchTuple
    ;
arrayTuple
    : values=expressionList
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
    ;

rightAssocExpr
    : powerExpr                             # PowerExpression_
    | op=(ADD|SUB|NOT) rightAssocExpr       # UnaryExpression       // priority=2
    ;

powerExpr
    : primaryExpr                           # PrimaryExpression_
    | primaryExpr op=POW rightAssocExpr     # PowerExpression       // priority=1
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
    | referExpr                 # ReferExpression
    | FUNC procedure            # LambdaExpression
    | '(' expression ')'        # ParenExpression
    | new                       # NewExpression
    ;

referExpr
    : symbol typeArguments?
    ;

argumentSet
    : '(' args=expressionList? ')'
    ;

expressionList
    : expression (COMMA expression)*
    ;






// init class & struct & union
objectExpr
    : '{' (objectEntry (COMMA objectEntry)*)? '}'
    ;
objectEntry
    : name=Identifier ASSIGN value=expression
    ;
// init array
arrayExpr
    : '[' elements=expressionList? ']'
    ;
// init with key-value pair
pairsExpr
    : '{' pair (COMMA pair)* '}'
    ;
pair
    : key=expression COLON value=expression
    ;



// array: fixed length at declaration
arrayDeclarer
    : '[' expression? ']'
    ;


// index: default for array
indexOf
    : '[' expression ']'
    ;

memberOf
    : DOT member=Identifier
    ;


// type for new
newType
    : definedType                       # NewDefinedType
    | '[' expression ']' typeDeclarer   # NewArrayType
    ;
new
    : NEW '(' newType (COMMA expression)? ')'
    ;
assert
    : QUESTION '(' typeDeclarer ')'
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
LET             : 'let' ;
NEW             : 'new' ;
// Keywords: Control
RETURN          : 'return' ;
IF              : 'if' ;
ELSE            : 'else' ;
FOR             : 'for' ;
CONTINUE        : 'continue' ;
BREAK           : 'break' ;
SWITCH          : 'switch' ;
CASE            : 'case' ;
FALLTHROUGH     : 'fallthrough' ;
DEFAULT         : 'default' ;
GOTO            : 'goto' ;
THROW           : 'throw' ;
TRY             : 'try' ;
CATCH           : 'catch' ;
FINALLY         : 'finally' ;

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