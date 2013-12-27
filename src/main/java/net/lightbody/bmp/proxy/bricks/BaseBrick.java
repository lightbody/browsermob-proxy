package net.lightbody.bmp.proxy.bricks;

import com.google.sitebricks.client.transport.Json;
import com.google.sitebricks.headless.Reply;
import net.lightbody.bmp.proxy.util.Log;
import net.lightbody.bmp.proxy.FeatureFlags;

public class BaseBrick {

    protected static FeatureFlags featureFlags = FeatureFlags.getInstance();

    protected static final Log LOG = new Log();


    public static class BrickEmptySuccessReply {
        private boolean success = true;

        public boolean getSuccess() {
            return this.success;
        }
    }

    public static class BrickSuccessReply {
        private boolean success = true;
        private Object data = null;

        public BrickSuccessReply() {
        }

        public boolean getSuccess() {
            return this.success;
        }

        public Object getData() {
            return this.data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }

    public static class BrickErrorReply {
        private boolean error = true;
        private Object  data  = null;

        public BrickErrorReply() {
        }

        public boolean getError() {
            return this.error;
        }

        public Object getData() {
            return this.data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }

    protected Reply<?> wrapSuccess(Object data) {
        if (!this.getEnhancedReplies()) {
            return Reply.with(data).as(Json.class);
        }

        BrickSuccessReply restReply = new BrickSuccessReply();
        restReply.setData(data);

        return Reply.with(restReply).as(Json.class);
    }

    protected Reply<?> wrapEmptySuccess() {
        if (!this.getEnhancedReplies()) {
            return Reply.saying().ok();
        }

        return Reply.with(new BrickEmptySuccessReply()).as(Json.class);
    }

    protected Reply<?> wrapError(Object data) {
        if (!this.getEnhancedReplies()) {
            return Reply.with(data).as(Json.class).error();
        }

        BrickErrorReply restReply = new BrickErrorReply();
        restReply.setData(data);

        return Reply.with(restReply).as(Json.class).error();
    }

    protected boolean getEnhancedReplies() {
        return false;
    }
}