package net.openesb.standalone.logging;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Color Console Handler for jdk: using ANSI sequences directly
 * 
 * @author David BRASSELY (brasseld at gmail.com)
 * @author OpenESB Community
 */
public class BaseColorConsoleHandler extends ConsoleHandler {
    protected static final String COLOR_RESET   = "\u001b[0m";

    protected static final String COLOR_SEVERE  = "\u001b[1;31m";
    protected static final String COLOR_WARNING = "\u001b[1;33m";
    protected static final String COLOR_INFO    = "\u001b[0;39m";
    protected static final String COLOR_CONFIG  = "\u001b[0;32m";
    protected static final String COLOR_FINE    = "\u001b[1;32m";
    protected static final String COLOR_FINER   = "\u001b[0;36m";
    protected static final String COLOR_FINEST  = "\u001b[1;30m";
    
    String logRecordToString(LogRecord record) {
        Formatter f = getFormatter();
        String msg = f.format(record);

        String prefix;
        Level level = record.getLevel();
        if (level == Level.SEVERE)
            prefix = COLOR_SEVERE;
        else if (level == Level.WARNING)
            prefix = COLOR_WARNING;
        else if (level == Level.INFO)
            prefix = COLOR_INFO;
        else if (level == Level.CONFIG)
            prefix = COLOR_CONFIG;
        else if (level == Level.FINE)
            prefix = COLOR_FINE;
        else if (level == Level.FINER)
            prefix = COLOR_FINER;
        else if (level == Level.FINEST)
            prefix = COLOR_FINEST;
        else
            // Unknown level, probably not possible, but if it happens it means it's bad :-)
            prefix = COLOR_SEVERE;

        return prefix + msg + COLOR_RESET;
    }
}
