package org.stepbible.textconverter.protocolagnosticutils

/******************************************************************************/
/**
* This package contains processing which is protocol-agnostic.
*
* By this, I mean that the processing works on USX or OSIS, and knows that it
* is doing so, but hopefully has been written in such a way that it doesn't
* mind which you pass to it -- the parameter you pass to the *process* method
* is sufficient to tell it which it is working on, and it should then cope.
*
* <span class='important>
* Having said this, at the time of writing all of the processing here is being
* applied only to OSIS, and I haven't tested it to see if it genuinely *can*
* cope with USX.</span
*
* Most of the classes here inherit from [PA], which defines certain minimal
* common processing.  A few need to have their own unique API, and therefore do
* not inherit from PA.
*
* @author ARA "Jamie" Jamieson
*/

interface AAA_Doc