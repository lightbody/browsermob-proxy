package net.lightbody.bmp.proxy.guice;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.sf.uadetector.service.UADetectorServiceFactory;

import java.io.IOException;

import static java.util.Arrays.asList;

public class ConfigModule implements Module {
    private String[] args;

    public ConfigModule(String[] args) {
        this.args = args;
    }

    @Override
    public void configure(Binder binder) {
        OptionParser parser = new OptionParser();

        ArgumentAcceptingOptionSpec<Integer> portSpec =
                parser.accepts("port", "The port to listen on")
                        .withOptionalArg().ofType(Integer.class).defaultsTo(8080);

        parser.acceptsAll(asList("help", "?"), "This help text");

        OptionSet options = parser.parse(args);

        if (options.has("?")) {
            try {
                parser.printHelpOn(System.out);
                System.exit(0);
            } catch (IOException e) {
                // should never happen, but...
                e.printStackTrace();
            }
            return;
        }

        binder.bind(Key.get(Integer.class, new NamedImpl("port"))).toInstance(portSpec.value(options));

        /*
         * Init User Agent String Parser, update of the UAS datastore will run in background.
         */
        UADetectorServiceFactory.getCachingAndUpdatingParser();
    }
}
