package org.stepbible.textconverter.builders

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import java.util.*

/******************************************************************************/
/**
 * Base class for special 'builders'.  These are things which do things like
 * evaluating how well the various Crosswire versification schemes fit a
 * given text, and don't actually build any of the 'normal' outputs.  Or in
 * some cases, may or may not do this.
 *
 * @author ARA "Jamie" Jamieson
 */

interface SpecialBuilder: Builder
