package net.lightbody.bmp.core.json;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

/**
 * 
 * @author Damien Jubeau <damien.jubeau@dareboost.com>
 * Allows Date Format to be compliant with Har 1.2 Spec : ISO 8601 with Time Zone Designator
 * @see https://github.com/lightbody/browsermob-proxy/issues/44
 *
 */

public class ISO8601WithTDZDateFormatter extends JsonSerializer<Date> {
    @Override
    public void serialize(java.util.Date value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonGenerationException {
        Calendar cal = Calendar.getInstance();
        cal.setTime(value);
        jgen.writeString(DatatypeConverter.printDateTime(cal));
    }

}
