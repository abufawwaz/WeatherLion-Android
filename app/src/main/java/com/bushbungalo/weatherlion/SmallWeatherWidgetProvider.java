package com.bushbungalo.weatherlion;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.bushbungalo.weatherlion.services.WidgetUpdateService;
import com.bushbungalo.weatherlion.utils.UtilityMethod;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID;

/*
 * Created by Paul O. Patterson on 11/7/17.
 */
@SuppressWarnings({"unused", "SameParameterValue"})
public class SmallWeatherWidgetProvider extends AppWidgetProvider
{
    private static String currentCondition = null;
    private static String sunriseTime = null;
    private static String sunsetTime = null;
    private static RemoteViews smallWidgetRemoteViews;

    private static final String TAG = "SmallWeatherWidgetProvider";
    private static final String LAUNCH_MAIN = "Main";
    private BroadcastReceiver mSystemBroadcastReceiver;

    private static AppWidgetManager mAppWidgetManager;

    public static final String REFRESH_BUTTON_CLICKED = "Refresh";
    public static final String WIDGET_UPDATE_MESSAGE = "WidgetUpdateMessage";
    public static final String ICON_REFRESH_MESSAGE = "WidgetIconRefreshMessage";

    /**
     * {@inheritDoc}
     */
    @Override
    public void onEnabled( Context context )
    {
        super.onEnabled( context );

        SharedPreferences spf = PreferenceManager.getDefaultSharedPreferences( context );
        String currLocation = spf.getString( WeatherLionApplication.CURRENT_LOCATION_PREFERENCE,
                Preference.DEFAULT_WEATHER_LOCATION );
        UtilityMethod.weatherWidgetEnabled
                = currLocation != null && !currLocation.equalsIgnoreCase(
                Preference.DEFAULT_WEATHER_LOCATION );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDisabled( Context context )
    {
        super.onDisabled( context );
        UtilityMethod.weatherWidgetEnabled = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpdate( Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds )
    {
        mAppWidgetManager = appWidgetManager;
        UtilityMethod.logMessage( UtilityMethod.LogLevel.INFO, "Update request received!",
                TAG + "::onUpdate" );

        ComponentName smallWidget = new ComponentName( context, SmallWeatherWidgetProvider.class );
        smallWidgetRemoteViews = new RemoteViews( context.getPackageName(),
                R.layout.wl_small_weather_widget_activity );

        int[] allWidgetIds = appWidgetManager.getAppWidgetIds( smallWidget );

        for ( int appWidgetId : allWidgetIds )
        {
            // Provider intent
            Intent intent = new Intent( context, SmallWeatherWidgetProvider.class );
            intent.putExtra( EXTRA_APPWIDGET_ID, appWidgetId );

            // set the applicable flags that main will use to start the service
            WeatherLionApplication.changeWidgetUnit =  false;

            Intent updateIntent = new Intent( context, WidgetUpdateService.class );
            updateIntent.putExtra( EXTRA_APPWIDGET_ID, appWidgetId );
            updateIntent.setData( Uri.parse( "false" ) );
            WidgetUpdateService.enqueueWork( context, updateIntent );

            // set the click listener for the refresh image
            PendingIntent refreshIntent = PendingIntent.getBroadcast( context,
                    0, intent, PendingIntent.FLAG_UPDATE_CURRENT );
            smallWidgetRemoteViews.setOnClickPendingIntent( R.id.imvRefresh, refreshIntent );

            smallWidgetRemoteViews.setOnClickPendingIntent(
                    R.id.imvRefresh,
                    getPendingSelfIntent( context, REFRESH_BUTTON_CLICKED )
            );

            PendingIntent mainIntent = PendingIntent.getBroadcast( context,
                    0, intent, PendingIntent.FLAG_UPDATE_CURRENT );
            smallWidgetRemoteViews.setOnClickPendingIntent( R.id.imvCurrentCondition, mainIntent );

            smallWidgetRemoteViews.setOnClickPendingIntent(
                    R.id.imvCurrentCondition,
                    getPendingSelfIntent( context, LAUNCH_MAIN )
            );

            Intent iconRefreshIntent = new Intent( context, SmallWeatherWidgetProvider.class );
            iconRefreshIntent.setAction( SmallWeatherWidgetProvider.ICON_REFRESH_MESSAGE );
            PendingIntent pendingIntent = PendingIntent.getBroadcast( context, 0,
                    iconRefreshIntent, 0 );

            appWidgetManager.updateAppWidget( smallWidget, smallWidgetRemoteViews);
        }// end of for each loop
    }// end of method onUpdate

    @Override
    public void onDeleted( Context context, int[] appWidgetIds )
    {
      super.onDeleted( context, appWidgetIds );
    }

    // Catch the click on widget views
    protected PendingIntent getPendingSelfIntent( Context context, String action )
    {
        Intent intent = new Intent( context, getClass() );
        intent.setAction( action );

        return PendingIntent.getBroadcast( context, 0, intent, 0 );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceive( Context context, Intent intent )
    {
        super.onReceive( context, intent );

        UtilityMethod.logMessage( UtilityMethod.LogLevel.INFO,
                intent.getAction() + " requested with action " +
                        intent.getStringExtra( WIDGET_UPDATE_MESSAGE ),
                TAG + "::onReceive" );

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance( context );
        smallWidgetRemoteViews = new RemoteViews( context.getPackageName(),
                R.layout.wl_small_weather_widget_activity );
        String updateExtra = intent.getStringExtra( WIDGET_UPDATE_MESSAGE );
        int[] appWidgetIds = AppWidgetManager.getInstance( context )
                .getAppWidgetIds( new ComponentName( context,
                        SmallWeatherWidgetProvider.class ) );

        // If the refresh button was clicked text clicked
        if ( REFRESH_BUTTON_CLICKED.equals( intent.getAction() ) )
        {
            UtilityMethod.refreshRequested = true;

            UtilityMethod.logMessage(UtilityMethod.LogLevel.INFO, "Refresh requested!",
                    TAG + "::onReceive");

            // Update the weather provider
            smallWidgetRemoteViews.setTextViewText( R.id.txvLastUpdated, "Refreshing..." );

            Intent refreshIntent = new Intent( context, WidgetUpdateService.class );
            refreshIntent.setData( Uri.parse( WeatherLionApplication.UNIT_NOT_CHANGED ) );
            WidgetUpdateService.enqueueWork( context, refreshIntent );

            // update the widget
            appWidgetManager.updateAppWidget( appWidgetIds, smallWidgetRemoteViews);

        }// end of if block
        else if ( LAUNCH_MAIN.equals( intent.getAction() ) )
        {
            Intent mainActivityIntent = new Intent( context, WeatherLionMain.class );
            context.startActivity( mainActivityIntent );
        }// end of else if block
        else
        {
            UtilityMethod.refreshRequested = false;
        }// end of else block

        if( updateExtra != null )
        {
            if( ICON_REFRESH_MESSAGE.equals( updateExtra ) )
            {
                ComponentName watchWidget = new ComponentName( context, SmallWeatherWidgetProvider.class );
                smallWidgetRemoteViews = new RemoteViews( context.getPackageName(),
                        R.layout.wl_small_weather_widget_activity );
                appWidgetManager.updateAppWidget( watchWidget, smallWidgetRemoteViews );
            }// end of if block
        }// end of if block
    }// end of method onReceive
}// end of class WeatherUpdater SmallWeatherWidgetProvider