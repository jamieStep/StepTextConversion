package org.stepbible.textconverter.support.miscellaneous

/******************************************************************************/
/**
* This package contains miscellaneous utilities.
*
* - **[Dom]** contains utilities for accessing and manipulating a document
*   object model.
*
* - **[LocaleHandler]** is intended mainly to assist when dealing with string
*   representations of numbers which may not necessarily be held in 'western'
*   form.  (I'm never to sure what to call this.  We often talk about Roman
*   text, but I can't describe this as Roman numerals.  And nor can I call it
*   Arabic without potential confusion with Arabic Arabic numerals.)
*
* - **[MarkerHandler]** is concerned with things like the callouts for
*   foonotes.  It lets you generates things as numbers, as fixed characters
*   (like the dagger which is often used in printed texts to mark footnotes),
*   or as potentially repeating character string (a, b, ... z, aa, ab ...).
*   If using this to generate callouts, bear in mind that currently STEP ignores
*   any callout supplied in the USX / OSIS, and always uses a down arrow as
*   callout.
*
* - **[MiscellaneousUtils]**, **[StepFileUtils]** and **[StepStringUtils]** all
*   do what it says on the tin (or in the case of MiscellaneousUtils, what it
*   *doesn't* say on the tin).
*
* - **[StepStringFormatter]** builds on top of the standard Java / Kotlin
*   string formatting utilities, to give additional flexibility and to
*   cater for including references in formatted strings.
*
* - **[Translations]** is concerned with supplying text in the vernacular
*   language where we have translations available.  (Where we do not, it
*   simply uses the English language version of the text.)
*
* - **[Zip]** creates zip the zip files needed for Sword modules.
*
* @author ARA 'Jamie' Jamieson
*/

private class _READ_ME_