// ========================================================================
// $Id: ServletIn.java,v 1.6 2004/05/09 20:32:27 gregwilkins Exp $
// Copyright 2000-2004 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package net.lightbody.bmp.proxy.jetty.jetty.servlet;

import net.lightbody.bmp.proxy.jetty.http.HttpInputStream;

import java.io.IOException;


class ServletIn extends HttpInputStream {
    HttpInputStream _in;

    /* ------------------------------------------------------------ */
    ServletIn(HttpInputStream in) {
        super(in);
        _in = in;
    }

    /* ------------------------------------------------------------ */
    @Override
    public int read()
            throws IOException {
        return _in.read();
    }

    /* ------------------------------------------------------------ */
    @Override
    public int read(byte b[]) throws IOException {
        return _in.read(b);
    }

    /* ------------------------------------------------------------ */
    @Override
    public int read(byte b[], int off, int len) throws IOException {
        return _in.read(b, off, len);
    }

    /* ------------------------------------------------------------ */
    @Override
    public long skip(long len) throws IOException {
        return _in.skip(len);
    }

    /* ------------------------------------------------------------ */
    @Override
    public int available()
            throws IOException {
        return _in.available();
    }

    /* ------------------------------------------------------------ */
    @Override
    public void close()
            throws IOException {
        _in.close();
    }

    /* ------------------------------------------------------------ */
    @Override
    public boolean markSupported() {
        return _in.markSupported();
    }

    /* ------------------------------------------------------------ */
    @Override
    public void reset()
            throws IOException {
        _in.reset();
    }

    /* ------------------------------------------------------------ */
    @Override
    public void mark(int readlimit) {
        _in.mark(readlimit);
    }

}


