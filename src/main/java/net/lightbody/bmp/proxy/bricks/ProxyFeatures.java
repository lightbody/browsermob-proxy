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
    @At("/enhancedReplies")
    public Reply<?> setEnhancedReplies(Request request) {
        this.logRequest("POST /features/enhancedReplies");

        String rawParam = request.param("enhancedReplies");
        if (rawParam == null)
        {
            return this.wrapError("Missing param enhancedReplies");
        }

        Boolean enhancedReplies = Boolean.parseBoolean(rawParam);
        this.logParam("enhancedReplies", enhancedReplies);

        featureFlags.setEnhancedReplies(enhancedReplies);

        return this.wrapEmptySuccess();
    }

    @Delete
    @At("/enhancedReplies")
    public Reply<?> deleteEnhancedReplies() {
        this.logRequest("DELETE /features/enhancedReplies");

        featureFlags.setEnhancedReplies(false);

        return this.wrapEmptySuccess();
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