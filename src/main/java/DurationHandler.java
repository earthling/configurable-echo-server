import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.Setter;

import io.dropwizard.util.Duration;

/**
 * Purpose of this class is to parse 'Duration's for args4j.
 */
public class DurationHandler extends OneArgumentOptionHandler<Duration>
{
    public DurationHandler(CmdLineParser parser, OptionDef option, Setter<? super Duration> setter)
    {
        super(parser, option, setter);
    }

    @Override
    protected Duration parse(String s) throws NumberFormatException, CmdLineException
    {
        return Duration.parse(s);
    }
}
