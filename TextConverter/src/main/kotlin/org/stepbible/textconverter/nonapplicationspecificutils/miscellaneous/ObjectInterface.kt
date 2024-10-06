package org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous

/******************************************************************************/
/**
 * This is not intended to add any functionality at all ...
 *
 * I need a way of identifying all objects -- and more particularly, those
 * objects which I think need to be initialised early.
 *
 * I have tried several ways of doing this ...
 *
 * - Simply hand-coding a method which mentions each object, thereby forcing
 *   it to be initialised.  This is fine, but for the need to remember to
 *   keep the list up to date.  And it has the advantage of not requiring any
 *   sophisticated facilities.
 *
 * - Using reflection to identify all objects and then automatically instantiate
 *   them.  Initially this looked to be difficult and there seemed to be
 *   suggestions that even code which purportedly worked was in fact rather
 *   finicky.  In fact I came up with something which seemed to be ok ... until
 *   I discovered that it only worked inside the IDE, and I couldn't find any
 *   useful information about the problem online.
 *
 * * Using reflection to identify all objects which inherited from a given
 *   interface, and then instantiating them -- the interface in question
 *   being the present one.  This seems to work both inside and outside of the
 *   IDE, and is the option I'm presently going for.  It suffers from the
 *   problems a) that the code is far from clear; and b) I have to remember
 *   to make objects inherit from ObjectInstance.  However, it will do for
 *   the time being; if I eventually find problems with it, I can always
 *   revert to the simple option above.
 *
 *
 * Why go to all this trouble?  Where we have utility classes with their own
 * local storage -- things like BibleBookNamesOsis, which contains a large table
 * listing book names -- init blocks are not inherently thread safe, and I'm not
 * sure what would happen if one thread attempted to use a previously
 * uninitialised instance of one of these utility classes and was then swapped
 * out in favour of another one.
 *
 * It is therefore convenient to initialise at the same time all objects where
 * this could be an issue.
 *
 * @author ARA "Jamie" Jamieson
 */

interface ObjectInterface