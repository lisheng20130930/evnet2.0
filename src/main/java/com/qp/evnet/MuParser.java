package com.qp.evnet;

import java.nio.ByteBuffer;

public class MuParser {
    public static final byte SPACE = 0x20;
    public static final byte CR = 0x0d;
    public static final byte LF = 0x0a;
    enum PState {
        s_uninitialized,
        s_start,
        s_start_boundary,
        s_header_field_start,
        s_header_field,
        s_headers_almost_done,
        s_header_value_start,
        s_header_value,
        s_header_value_almost_done,
        s_part_data_start,
        s_part_data,
        s_part_data_almost_boundary,
        s_part_data_boundary,
        s_part_data_almost_end,
        s_part_data_end,
        s_part_data_final_hyphen,
        s_end
    }
    private int index;
    private int boundary_length;
    private PState state = PState.s_uninitialized;

    private MuParserSettings settings;
    private byte[] lookbehind;
    private byte[] multipart_boundary;

    public static MuParser createMuParser(String boundary, MuParserSettings settings){
        MuParser p = new MuParser();
        p.multipart_boundary = boundary.getBytes();
        p.boundary_length = boundary.length();
        p.index = 0;
        p.lookbehind = new byte[(boundary.length() + 9)];
        p.state = PState.s_start;
        p.settings = settings;
        return p;
    }

    public int multipart_parser_execute(ByteBuffer buffer){
        byte[] buf = buffer.array();
        int len = buffer.limit();
        int i = buffer.position();
        int mark = 0;
        byte c, cl;
        boolean is_last = false;

        while(i < len) {
            c = buf[i];
            is_last = (i == (len - 1));
            switch (state) {
                case s_start:
                    index = 0;
                    state = PState.s_start_boundary;

                    /* fallthrough */
                case s_start_boundary:
                    if (index == boundary_length) {
                        if (c != CR) {
                            return i;
                        }
                        index++;
                        break;
                    } else if (index == (boundary_length + 1)) {
                        if (c != LF) {
                            return i;
                        }
                        index = 0;
                        settings.call_on_part_data_begin(this);
                        state = PState.s_header_field_start;
                        break;
                    }
                    if (c != multipart_boundary[index]) {
                        return i;
                    }
                    index++;
                    break;

                case s_header_field_start:
                    mark = i;
                    state = PState.s_header_field;

                    /* fallthrough */
                case s_header_field:
                    if (c == CR) {
                        state = PState.s_headers_almost_done;
                        break;
                    }

                    if (c == (byte)'-') {
                        break;
                    }

                    if (c == (byte)':') {
                        settings.call_on_header_field(this,buffer, mark, i - mark);
                        state = PState.s_header_value_start;
                        break;
                    }

                    cl = (byte) (c | 0x20);
                    if (cl < ((byte)'a') || cl > ((byte)'z')) {
                        return i;
                    }
                    if (is_last)
                        settings.call_on_header_field(this, buffer, mark, (i - mark) + 1);
                    break;

                case s_headers_almost_done:
                    if (c != LF) {
                        return i;
                    }

                    state = PState.s_part_data_start;
                    break;

                case s_header_value_start:
                    if (c == SPACE) {
                        break;
                    }

                    mark = i;
                    state = PState.s_header_value;

                    /* fallthrough */
                case s_header_value:
                    if (c == CR) {
                        settings.call_on_header_value(this, buffer, mark, i - mark);
                        state = PState.s_header_value_almost_done;
                    }
                    if (is_last)
                        settings.call_on_header_value(this, buffer, mark, (i - mark) + 1);
                    break;

                case s_header_value_almost_done:
                    if (c != LF) {
                        return i;
                    }
                    state = PState.s_header_field_start;
                    break;

                case s_part_data_start:
                    settings.call_on_headers_complete(this);
                    mark = i;
                    state = PState.s_part_data;

                    /* fallthrough */
                case s_part_data:
                    if (c == CR) {
                        settings.call_on_part_data(this, buffer, mark, i - mark);
                        mark = i;
                        state = PState.s_part_data_almost_boundary;
                        lookbehind[0] = CR;
                        break;
                    }
                    if (is_last)
                        settings.call_on_part_data(this, buffer, mark, (i - mark) + 1);
                    break;

                case s_part_data_almost_boundary:
                    if (c == LF) {
                        state = PState.s_part_data_boundary;
                        lookbehind[1] = LF;
                        index = 0;
                        break;
                    }
                    settings.call_on_part_data(this, ByteBuffer.wrap(lookbehind), 0, 1);
                    state = PState.s_part_data;
                    mark = i --;
                    break;

                case s_part_data_boundary:
                    if (multipart_boundary[index] != c) {
                        settings.call_on_part_data(this, ByteBuffer.wrap(lookbehind), 0,2 + index);
                        state = PState.s_part_data;
                        mark = i --;
                        break;
                    }
                    lookbehind[2 + index] = c;
                    if ((++ index) == boundary_length) {
                        settings.call_on_part_data_end(this);
                        state = PState.s_part_data_almost_end;
                    }
                    break;

                case s_part_data_almost_end:
                    if (c == (byte)'-') {
                        state = PState.s_part_data_final_hyphen;
                        break;
                    }
                    if (c == CR) {
                        state = PState.s_part_data_end;
                        break;
                    }
                    return i;

                case s_part_data_final_hyphen:
                    if (c == (byte)'-') {
                        settings.call_on_body_end(this);
                        state = PState.s_end;
                        break;
                    }
                    return i;

                case s_part_data_end:
                    if (c == LF) {
                        state = PState.s_header_field_start;
                        settings.call_on_part_data_begin(this);
                        break;
                    }
                    return i;

                case s_end:
                    break;

                default:
                    return 0;
            }
            ++ i;
        }

        return len-buffer.position();
    }

    public static class MuParserSettings {
        public IMultipart_data_cb on_header_field;
        public IMultipart_data_cb on_header_value;
        public IMultipart_data_cb on_part_data;

        public IMultipart_notify_cb on_part_data_begin;
        public IMultipart_notify_cb on_headers_complete;
        public IMultipart_notify_cb on_part_data_end;
        public IMultipart_notify_cb on_body_end;

        void call_on_header_field(MuParser p, ByteBuffer buf, int pos, int len) {
            call_on(on_header_field, p, buf, pos, len);
        }
        void call_on_header_value(MuParser p, ByteBuffer buf, int pos, int len) {
            call_on(on_header_value, p, buf, pos, len);
        }
        void call_on_part_data(MuParser p, ByteBuffer buf, int pos, int len) {
            call_on(on_part_data, p, buf, pos, len);
        }
        void call_on_part_data_begin(MuParser p) {
            call_on(on_part_data_begin, p);
        }
        void call_on_headers_complete(MuParser p) {
            call_on(on_headers_complete, p);
        }
        void call_on_part_data_end(MuParser p) {
            call_on(on_part_data_end, p);
        }
        void call_on_body_end(MuParser p) {
            call_on(on_body_end, p);
        }

        void call_on(IMultipart_notify_cb cb, MuParser p) {
            if (null != cb) {
                cb.cb(p);
            }
        }
        void call_on(IMultipart_data_cb cb, MuParser p, ByteBuffer buf, int pos, int len) {
            if (null != cb && -1 != pos) {
                cb.cb(p,buf,pos,len);
            }
        }
        public interface IMultipart_data_cb {
            int cb(MuParser p, ByteBuffer buf, int pos, int len);
        }
        public interface IMultipart_notify_cb {
            int cb(MuParser p);
        }
    }
}
