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
        this.logRequest("GET /features");

        try {
            return(this.wrapSuccess(featureFlags));
        }
        catch (Exception e) {
            return this.wrapError(e);
        }
    }

    @Post
    @At("/requestLogs")
    public Reply<?> setRequestLogs(Request request) {
        this.logRequest("POST /features/requestLogs");

        String rawParam = request.param("requestLogs");
        if (rawParam == null)
        {
            return this.wrapError("Missing param requestLogs");
        }

        Boolean requestLogs = Boolean.parseBoolean(rawParam);
        this.logParam("requestLogs", requestLogs);

        featureFlags.setRequestLogs(requestLogs);

        return this.wrapEmptySuccess();
    }

    @Delete
    @At("/requestLogs")
    public Reply<?> deleteRequestLogs() {
        this.logRequest("DELETE /features/requestLogs");

        featureFlags.setRequestLogs(false);

        return this.wrapEmptySuccess();
    }
}