package net.lightbody.bmp.proxy.bricks;

import com.google.sitebricks.At;
import com.google.sitebricks.headless.Reply;
import com.google.sitebricks.headless.Request;
import com.google.sitebricks.headless.Service;
import com.google.sitebricks.http.Delete;
import com.google.sitebricks.http.Get;
import com.google.sitebricks.http.Post;

import net.lightbody.bmp.proxy.FeatureFlags;

@At("/features")
@Service
public class ProxyFeatures extends BaseBrick {

    @Get
    public Reply<?> getFeatures() {
        try {
            return(this.wrapSuccess(featureFlags));
        }
        catch (Exception e) {
            return this.wrapError(e);
        }
    }
}