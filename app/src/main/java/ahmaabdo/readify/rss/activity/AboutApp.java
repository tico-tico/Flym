package ahmaabdo.readify.rss.activity;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.webkit.WebView;

import com.danielstone.materialaboutlibrary.ConvenienceBuilder;
import com.danielstone.materialaboutlibrary.MaterialAboutActivity;
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem;
import com.danielstone.materialaboutlibrary.items.MaterialAboutItemOnClickAction;
import com.danielstone.materialaboutlibrary.items.MaterialAboutTitleItem;
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard;
import com.danielstone.materialaboutlibrary.model.MaterialAboutList;

import ahmaabdo.readify.rss.R;
import ahmaabdo.readify.rss.utils.PrefUtils;

/**
 * Created by Ahmad on Oct 28, 2017.
 */

public class AboutApp extends MaterialAboutActivity {

    @Override
    protected MaterialAboutList getMaterialAboutList(final Context context) {
        if (!PrefUtils.getBoolean(PrefUtils.LIGHT_THEME, true))
            setTheme(R.style.AppTheme_DarkMaterialAboutActivity);

        MaterialAboutCard.Builder appCardBuilder = new MaterialAboutCard.Builder();

        appCardBuilder.addItem(new MaterialAboutTitleItem.Builder()
                .text("Readify RSS")
                .desc("© 2018 Ahmad Abdo")
                .icon(R.mipmap.ic_launcher)
                .build());

        try {
            appCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                    .text("Version")
                    .subText(getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName)
                    .icon(R.drawable.ic_about_info)
                    .build());
        } catch (PackageManager.NameNotFoundException e) {
            appCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                    .text("Version")
                    .subText("1.5")
                    .icon(R.drawable.ic_about_info)
                    .build());
        }

        appCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Rate this app")
                .icon(R.drawable.ic_rate_review)
                .setOnClickAction(ConvenienceBuilder.createRateOnClickAction(context))
                .build());

        appCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Licenses")
                .icon(R.drawable.ic_book_black)
                .setOnClickAction(new MaterialAboutItemOnClickAction() {
                    @Override
                    public void onClick() {
                        WebView web = (WebView) LayoutInflater.from(context).inflate(R.layout.dialog_licenses, null);
                        web.loadUrl("file:///android_asset/open_source_licenses.html");
                        new AlertDialog.Builder(context, R.style.Theme_AppCompat_Light_Dialog_Alert)
                                .setTitle("Licenses")
                                .setView(web)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                })
                .build());


        MaterialAboutCard.Builder authorCardBuilder = new MaterialAboutCard.Builder();
        authorCardBuilder.title("Author");

        authorCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Ahmad Abdo")
                .icon(R.drawable.ic_account_box)
                .build());

        authorCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("GitHub")
                .icon(R.drawable.github)
                .setOnClickAction(ConvenienceBuilder.createWebsiteOnClickAction(context, Uri.parse("https://github.com/ahmaabdo")))
                .build());


        MaterialAboutCard.Builder convenienceCardBuilder = new MaterialAboutCard.Builder();

        convenienceCardBuilder.title("Convenience Builder");

        try {
            convenienceCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                    .text("Version")
                    .subText(getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName)
                    .icon(R.drawable.ic_about_info)
                    .build());
        } catch (PackageManager.NameNotFoundException e) {
            convenienceCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                    .text("Version")
                    .subText("1.2.1")
                    .icon(R.drawable.ic_about_info)
                    .build());
        }

        convenienceCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Fork on GitHub")
                .subText("https://github.com/ahmaabdo/ReadifyRSS")
                .icon(R.drawable.github)
                .setOnClickAction(ConvenienceBuilder.createWebsiteOnClickAction(context, Uri.parse("https://github.com/ahmaabdo/ReadifyRSS")))
                .build());

        convenienceCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Send an email")
                .subText("a7maabdo@gmail.com")
                .icon(R.drawable.ic_email)
                .setOnClickAction(ConvenienceBuilder.createEmailOnClickAction(context, "a7maabdo@gmail.com", "ReadifyRSS feedback"))
                .build());

        convenienceCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Visit Egypt")
                .subText("Pyramids and Sphinx")
                .icon(R.drawable.ic_directions)
                .setOnClickAction(ConvenienceBuilder.createMapOnClickAction(context, "Egypt"))
                .build());

        MaterialAboutCard.Builder otherCardBuilder = new MaterialAboutCard.Builder();
        otherCardBuilder.title("About Readify");

        otherCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .icon(R.drawable.ic_developer_mode)
                .text("Main Developers")
                .subTextHtml(getString(R.string.about_us_content))
                .setIconGravity(MaterialAboutActionItem.GRAVITY_TOP)
                .build()
        );

        return new MaterialAboutList.Builder()
                .addCard(appCardBuilder.build())
                .addCard(authorCardBuilder.build())
                .addCard(convenienceCardBuilder.build())
                .addCard(otherCardBuilder.build())
                .build();
    }


    @Override
    protected CharSequence getActivityTitle() {
        return getString(R.string.mal_title_about);
    }

}