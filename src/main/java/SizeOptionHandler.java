import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.Setter;

import io.dropwizard.util.Size;

/**
 * Purpose of this class is to parse 'Size's for args4j.
 */
public class SizeOptionHandler extends OneArgumentOptionHandler<Size>
{
    public SizeOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super Size> setter)
    {
        super(parser, option, setter);
    }

    @Override
    protected Size parse(String s) throws NumberFormatException, CmdLineException
    {
        return Size.parse(s);
    }
}
