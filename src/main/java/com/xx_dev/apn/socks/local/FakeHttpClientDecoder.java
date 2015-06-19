/*
 * Copyright (c) 2014 The APN-PROXY Project
 *
 * The APN-PROXY Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.xx_dev.apn.socks.local;

import com.xx_dev.apn.socks.common.utils.TextUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import org.apache.commons.lang.StringUtils;

import java.util.List;

/**
 * @author xmx
 * @version $Id: com.xx_dev.apn.proxy.ApnProxyAESDecoder 14-6-28 12:09 (xmx) Exp $
 */
public class FakeHttpClientDecoder extends ReplayingDecoder<FakeHttpClientDecoder.STATE> {

    enum STATE {
        READ_FAKE_HTTP,
        READ_CONTENT
    }

    private int flag = 0;

    private ByteBuf headBuf = Unpooled.buffer();

    private int length;


    public FakeHttpClientDecoder() {
        super(STATE.READ_FAKE_HTTP);


    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (this.state()) {
        case READ_FAKE_HTTP: {
            for (;;) {
                byte b = in.readByte();
                headBuf.writeByte(b);

                if (b == '\r' || b == '\n') {
                    flag ++;
                } else if (flag > 0){
                    flag = 0;
                }

                if (flag >= 4) {
                    byte[] buf = new byte[headBuf.readableBytes()];
                    headBuf.readBytes(buf);
                    headBuf.clear();
                    String s = TextUtil.fromUTF8Bytes(buf);
                    String[] ss = StringUtils.split(s, "\r\n");


                    for (String line : ss) {
                        if (StringUtils.startsWith(line, "X-C:")) {
                            String lenStr = StringUtils.trim(StringUtils.split(line, ":")[1]);
                            length = Integer.parseInt(lenStr, 16);
                        }
                    }

                    flag = 0;
                    this.checkpoint(STATE.READ_CONTENT);
                    break;
                }
            }
        }
        case READ_CONTENT: {
            if (length > 0) {
                byte[] buf = new byte[length];
                in.readBytes(buf, 0, length);

                byte[] res = new byte[length];

                for (int i = 0; i < length; i++) {
                    res[i] = (byte) (buf[i] ^ (LocalConfig.ins().getEncryptKey() & 0xFF));
                }

                ByteBuf outBuf = ctx.alloc().buffer();

                outBuf.writeBytes(res);

                out.add(outBuf);
            }

            this.checkpoint(STATE.READ_FAKE_HTTP);
            break;
        }
        default:
            throw new Error("Shouldn't reach here.");
        }


    }


}
