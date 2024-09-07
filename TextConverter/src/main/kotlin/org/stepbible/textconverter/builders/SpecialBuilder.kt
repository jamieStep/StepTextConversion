package org.stepbible.textconverter.builders

/******************************************************************************/
/**
 * Base class for 'special' builders.  These are things which do things like
 * evaluating how well the various Crosswire versification schemes fit a
 * given text, and don't actually build any of the 'normal' outputs.
 *
 * This adds no functionality to the base [BuilderRoot] class.  I use it
 * simply as a means of distinguishing special builders.  See [BuilderRoot]
 * for more details.
 *
 * @author ARA "Jamie" Jamieson
 */

abstract class SpecialBuilder: BuilderRoot()
