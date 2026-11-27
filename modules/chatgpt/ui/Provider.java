package com.mikeyphw.hooklab.moduleui.chatgpt;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.mikeyphw.hooklab.packs.ModuleUiProvider;
import com.mikeyphw.hooklab.sdk.ChatGptConfig;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Example personal native page. Review this code before signing and admitting it. */
public final class Provider implements ModuleUiProvider {
    private LinearLayout root;
    @Override public View create(Host host) {
        if(root!=null||!"chatgpt".equals(host.moduleId()))throw new IllegalStateException("provider identity");
        Locale locale=host.themedContext().getResources().getConfiguration().getLocales().get(0);
        root=new LinearLayout(host.themedContext());root.setOrientation(LinearLayout.VERTICAL);
        root.addView(host.heading(host.resources().translation(host.moduleId(),"title",locale,"ChatGPT custom settings")));
        TextView help=new TextView(host.themedContext());
        help.setText(new String(host.resources().asset(host.moduleId(),"guide").bytes(),StandardCharsets.UTF_8));root.addView(help);
        try {host.chatGptSettings();root.addView(host.button(host.resources().translation(host.moduleId(),"reset",locale,"Review defaults"),()->host.reviewChatGptSettings(ChatGptConfig.defaults())));}
        catch(IllegalStateException unavailable){TextView message=new TextView(host.themedContext());message.setText(host.resources().translation(host.moduleId(),"unavailable",locale,"Configuration unavailable"));root.addView(message);}
        return root;
    }
    @Override public void destroy(){if(root!=null){root.removeAllViews();root=null;}}
}
