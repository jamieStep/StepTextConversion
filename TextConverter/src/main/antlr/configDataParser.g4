/******************************************************************************/
/*
   Grammar for the right hand side of configuration assignment statements.

   These contain some combination of the following:

   - SomeGeneralText: This is any text not enclosed in quotes.  It is handled
     by miscellaneousText below, and its value is just that text.

   - $myVariable: This is a reference to another configuration parameter. It
     is handled by variableRef.  The variable name may be any combination of
     word characters.  Occasionally you may wish to have a variable name
     immediately followed by other word characters.  In these cases, you can
     use $$ to mark the end of the variable name.

   - $myFunc(arg1, arg2, arg3, ...): This is a function call.  There can be as
     few or as many arguments as the function requires / accepts.  Each
     argument may be a _quoted_ text string (in straight double quotes), a
     variable reference, or another function call.

   - Quoted text strings evaluate to the string inside the quote marks.  Within
     a quoted text string, in theory you can include a double quote by preceding
     it with a backslash.  I've never tried this, though.

   - You cannot use a dollar sign as a stand-alone character: it is always
     interpreted as implying that we have either a function call or a variable
     reference.

   - An expression can be built out of a sequence of zero or more of the above.
     If the expression consists of only a single item, then it is permitted to
     evaluate to null.  If it contains more than one item, then none of them is
     permitted to be null.


   This will look more complicated than you might expect it to be.  The
   complication arises mainly because of the need to be able to ignore
   whitespace between the arguments of function calls, but retain it in
   quoted strings, and in non-quoted strings outside of function calls.


   If you make changes here, you need to rebuild the code which depends upon this
   data.  In the IDEA command line window, run

       ./gradlew clean generateGrammarSource build

   (the 'clean' is optional, but useful if things have been going wrong).
*/

grammar configDataParser;

file: expressionSequence EOF;

expressionSequence: topLevelExpression+;

topLevelExpression
    : functionCall
    | variableRef
    | quotedString
    | miscellaneousText
    ;

argument
    : functionCall
    | variableRef
    | quotedString
    ;

functionCall: DOLLAR_IDENT BRA arguments? KET;

arguments: argument (COMMA argument)*;

// Allow "unstructured" text not matching other rules
miscellaneousText
    : (MISC | COMMA | BRA | KET)+
    ;

variableRef: DOLLAR_IDENT (DOLLAR_END)?;
quotedString: QUOTED_STRING;

WS : [ \t\r\n]+ -> skip ;

DOLLAR_IDENT  : '$' [a-zA-Z_][a-zA-Z0-9_]* ;
DOLLAR_END    : '$$' ;
QUOTED_STRING : '"' ( ~["\\] | '\\' . )* '"' ;
COMMA         : ',' ;
BRA           : '(' ;
KET           : ')' ;

// This absorbs everything that's not a special token
MISC : . ;

