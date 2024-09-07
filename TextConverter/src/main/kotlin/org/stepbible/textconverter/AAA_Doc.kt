package org.stepbible.textconverter

/******************************************************************************/
/**
* The various packages are as follows:
*
* - **[org.stepbible.textconverter.applicationspecificutils]:** Utilities
*   which are specific to the text converter -- the sort of thing which might
*   perhaps be fairly widely used within the converter, or at least would afford
*   a convenient opportunity to factor out functionality, but whose nature is
*   intimately bound to yhe philosophy of the converter.
*
* - **[org.stepbible.textconverter.builders]:** In the main, this covers things
*   which between them control the overall text conversion process.
*
* - **[org.stepbible.textconverter.nonapplicationspecificutils]:** Utilities
*   which in theory are not specific to the text converter -- the sort of thing
*   which might well be of use, if not in a general application, at least in one
*   similar to the converter and working with the same kinds of inputs and
*   outputs.
*
* - **[org.stepbible.textconverter.osisonly]:** Code which operates specifically
*   upon OSIS.
*
* - **[org.stepbible.textconverter.protocolagnosticutils]:** Code which operates
*   upon either USX *or* OSIS, and (hopefully) can cope with either without
*   needing to be changed.  This has been written this way in case at any stage
*   in the future we decide to apply a lot of the processing which currently is
*   applied to OSIS instead to USX.  At the time of writing it has been
*   exercised only with OSIS, and there is no guarantee it will actually work
*   correctly with USX.
*
* - **[org.stepbible.textconverter.usxonly]:** Code which operates specifically
*   upon USX.
*
*
* The Resources section of this JAR file contains a lot of data used in support
* of the processing -- configuration data, standard tables of information, etc.
* It contains a _READ_ME_.txt which gives more information.
*/

interface AAA_Doc