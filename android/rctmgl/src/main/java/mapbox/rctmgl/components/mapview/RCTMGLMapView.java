package mapbox.rctmgl.components.mapview;

import android.content.Context;
import android.graphics.PointF;
import android.location.Location;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.RelativeLayout;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.MapboxMapOptions;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.UiSettings;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.services.android.location.LostLocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngineListener;
import com.mapbox.services.android.telemetry.location.LocationEnginePriority;
import com.mapbox.services.commons.geojson.FeatureCollection;
import com.mapbox.services.commons.geojson.Point;

import mapbox.rctmgl.events.IEvent;
import mapbox.rctmgl.events.MapChangeEvent;
import mapbox.rctmgl.events.MapClickEvent;
import mapbox.rctmgl.events.UserLocationChangeEvent;
import mapbox.rctmgl.events.constants.EventTypes;
import mapbox.rctmgl.utils.ConvertUtils;
import mapbox.rctmgl.utils.SimpleEventCallback;

/**
 * Created by nickitaliano on 8/18/17.
 */

@SuppressWarnings({"MissingPermission"})
public class RCTMGLMapView extends RelativeLayout implements
        OnMapReadyCallback, MapboxMap.OnMapClickListener, MapboxMap.OnMapLongClickListener,
        MapView.OnMapChangedListener, LocationEngineListener
{
    public static final String LOG_TAG = RCTMGLMapView.class.getSimpleName();

    private RCTMGLMapViewManager mManager;
    private Context mContext;

    private MapboxMap mMap;
    private MapView mMapView;
    private LocationEngine mLocationEngine;
    private LocationLayerPlugin mLocationLayer;

    private String mStyleURL;

    private boolean mAnimated;
    private boolean mScrollEnabled;
    private boolean mPitchEnabled;
    private boolean mShowUserLocation;

    private int mUserTrackingMode;

    private double mHeading;
    private double mPitch;
    private double mZoomLevel;

    private Double mMinZoomLevel;
    private Double mMaxZoomLevel;

    private Point mCenterCoordinate;

    public RCTMGLMapView(Context context, RCTMGLMapViewManager manager) {
        super(context);
        mContext = context;
        mManager = manager;
    }

    public void dispose() {
        if (mLocationEngine != null) {
            mLocationEngine.removeLocationEngineListener(this);
            mLocationEngine.deactivate();
        }
    }

    //region Map Callbacks

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        if (mMapView == null) {
            Log.d(LOG_TAG, "Mapbox map is ready before our mapview is initialized!!!");
            return;
        }
        mMap = mapboxMap;

        mMap.setOnMapClickListener(this);
        mMap.setOnMapLongClickListener(this);
        mMapView.addOnMapChangedListener(this);

        // in case props were set before the map was ready lets set them
        updateUISettings();
        setMinMaxZoomLevels();

        if (mShowUserLocation) {
            enableLocationLayer();
        }
    }

    @Override
    public void onMapClick(@NonNull LatLng point) {
        PointF screenPoint = mMap.getProjection().toScreenLocation(point);
        MapClickEvent event = new MapClickEvent(this, point, screenPoint);
        mManager.handleEvent(event);
    }

    @Override
    public void onMapLongClick(@NonNull LatLng point) {
        PointF screenPoint = mMap.getProjection().toScreenLocation(point);
        MapClickEvent event = new MapClickEvent(this, point, screenPoint, EventTypes.MAP_LONG_CLICK);
        mManager.handleEvent(event);
    }

    @Override
    public void onMapChanged(int changed) {
        IEvent event = null;

        switch (changed) {
            case MapView.REGION_WILL_CHANGE:
                event = new MapChangeEvent(this, makeRegionPayload(false), EventTypes.REGION_WILL_CHANGE);
                break;
            case MapView.REGION_WILL_CHANGE_ANIMATED:
                event = new MapChangeEvent(this, makeRegionPayload(true), EventTypes.REGION_WILL_CHANGE);
                break;
            case MapView.REGION_IS_CHANGING:
                event = new MapChangeEvent(this, EventTypes.REGION_IS_CHANGING);
                break;
            case MapView.REGION_DID_CHANGE:
                event = new MapChangeEvent(this, makeRegionPayload(false), EventTypes.REGION_WILL_CHANGE);
                break;
            case MapView.REGION_DID_CHANGE_ANIMATED:
                event = new MapChangeEvent(this, makeRegionPayload(true), EventTypes.REGION_DID_CHANGE);
                break;
            case MapView.WILL_START_LOADING_MAP:
                 event = new MapChangeEvent(this, EventTypes.WILL_START_LOADING_MAP);
                break;
            case MapView.DID_FAIL_LOADING_MAP:
                event = new MapChangeEvent(this, EventTypes.DID_FAIL_LOADING_MAP);
                break;
            case MapView.DID_FINISH_LOADING_MAP:
                event = new MapChangeEvent(this, EventTypes.DID_FINISH_LOADING_MAP);
                break;
            case MapView.WILL_START_RENDERING_FRAME:
                event = new MapChangeEvent(this, EventTypes.WILL_START_RENDERING_FRAME);
                break;
            case MapView.DID_FINISH_RENDERING_FRAME:
                event = new MapChangeEvent(this, EventTypes.DID_FINISH_RENDERING_FRAME);
                break;
            case MapView.DID_FINISH_RENDERING_FRAME_FULLY_RENDERED:
                event = new MapChangeEvent(this, EventTypes.DID_FINISH_RENDERING_FRAME_FULLY);
                break;
            case MapView.WILL_START_RENDERING_MAP:
                event = new MapChangeEvent(this, EventTypes.WILL_START_RENDERING_MAP);
                break;
            case MapView.DID_FINISH_RENDERING_MAP:
                event = new MapChangeEvent(this, EventTypes.DID_FINISH_RENDERING_MAP);
                break;
            case MapView.DID_FINISH_RENDERING_MAP_FULLY_RENDERED:
                event = new MapChangeEvent(this, EventTypes.DID_FINISH_RENDERING_MAP_FULLY);
                break;
            case MapView.DID_FINISH_LOADING_STYLE:
                event = new MapChangeEvent(this, EventTypes.DID_FINISH_LOADING_STYLE);
                break;
        }

        if (event != null) {
            mManager.handleEvent(event);
        }
    }

    //endregion

    //region Property getter/setters

    public void setStyleURL(String styleURL) {
        mStyleURL = styleURL;

        if (mMap != null) {
            mMap.setStyleUrl(styleURL);
        }
    }

    public void setAnimated(boolean animated) {
        mAnimated = animated;
        updateCameraPositionIfNeeded(false);
    }

    public void setScrollEnabled(boolean scrollEnabled) {
        mScrollEnabled = scrollEnabled;
        updateUISettings();
    }

    public void setPitchEnabled(boolean pitchEnabled) {
        mPitchEnabled = pitchEnabled;
        updateUISettings();
    }

    public void setHeading(double heading) {
        mHeading = heading;
        updateCameraPositionIfNeeded(false);
    }

    public void setPitch(double pitch) {
        mPitch = pitch;
        updateCameraPositionIfNeeded(false);
    }

    public void setZoomLevel(double zoomLevel) {
        mZoomLevel = zoomLevel;
        updateCameraPositionIfNeeded(false);
    }

    public void setMinZoomLevel(double minZoomLevel) {
        mMinZoomLevel = minZoomLevel;
        setMinMaxZoomLevels();
    }

    public void setMaxZoomLevel(double maxZoomLevel) {
        mMaxZoomLevel = maxZoomLevel;
        setMinMaxZoomLevels();
    }

    public void setCenterCoordinate(Point centerCoordinate) {
        mCenterCoordinate = centerCoordinate;
        updateCameraPositionIfNeeded(true);
    }

    public void setShowUserLocation(boolean showUserLocation) {
        mShowUserLocation = showUserLocation;

        if (mLocationEngine != null) {
            // deactive location engine if we are hiding the location layer
            if (!mShowUserLocation) {
                mLocationEngine.deactivate();
                return;
            }

            if (mMap != null) {
                enableLocationLayer();
            }
        }
    }

    public void setUserTrackingMode(int userTrackingMode) {
        mUserTrackingMode = userTrackingMode;

        if (mLocationLayer != null) {
            mLocationLayer.setLocationLayerEnabled(mUserTrackingMode);
        }
    }

    //endregion

    //region Methods

    public void flyTo(Point flyToPoint, int durationMS) {
        final IEvent event = new MapChangeEvent(this, EventTypes.FLY_TO_COMPLETE);

        CameraPosition nextPosition = new CameraPosition.Builder(mMap.getCameraPosition())
                .target(ConvertUtils.toLatLng(flyToPoint))
                .build();

        CameraUpdate flyToUpdate = CameraUpdateFactory.newCameraPosition(nextPosition);
        mMap.animateCamera(flyToUpdate, durationMS, new SimpleEventCallback(mManager, event));
    }

    public void setCamera(ReadableMap map) {
        final IEvent event = new MapChangeEvent(this, EventTypes.SET_CAMERA_COMPLETE);
        CameraPosition.Builder builder = new CameraPosition.Builder(mMap.getCameraPosition());

        if (map.hasKey("pitch")) {
            builder.tilt(map.getDouble("pitch"));
        }

        if (map.hasKey("heading")) {
            builder.bearing(map.getDouble("heading"));
        }

        if (map.hasKey("centerCoordinate")) {
            Point target = ConvertUtils.toPointGemetry(map.getString("centerCoordinate"));
            builder.target(ConvertUtils.toLatLng(target));
        }

        int durationMS = 2000;
        if (map.hasKey("duration")) {
            durationMS = map.getInt("duration");
        }

        CameraUpdate update = CameraUpdateFactory.newCameraPosition(builder.build());
        mMap.easeCamera(update, durationMS);
        mMap.easeCamera(update, durationMS, new SimpleEventCallback(mManager, event));
    }

    public void fitBounds(FeatureCollection featureCollection, int padding, int durationMS) {
        LatLngBounds bounds = ConvertUtils.toLatLngBounds(featureCollection);
        if (bounds == null) {
            return;
        }
        mMap.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding), durationMS);
    }

    //endregion

    @Override
    public void onConnected() {
        mLocationEngine.requestLocationUpdates();

        Location location = mLocationEngine.getLastLocation();
        if (location != null) {
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        IEvent event = new UserLocationChangeEvent(this, location);
        mManager.handleEvent(event);

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
    }

    public void makeView() {
        if (mMapView != null) {
            return;
        }
        buildMapView();
    }

    private void buildMapView() {
        MapboxMapOptions options = new MapboxMapOptions();
        options.camera(buildCamera());

        mMapView = new MapView(getContext(), options);
        mManager.addView(this, mMapView, 0);
        mMapView.setStyleUrl(mStyleURL);
        mMapView.onCreate(null);
        mMapView.getMapAsync(this);
    }

    private void updateCameraPositionIfNeeded(boolean shouldUpdateTarget) {
        if (mMap != null) {
            CameraPosition prevPosition = mMap.getCameraPosition();
            CameraUpdate cameraUpdate = CameraUpdateFactory.newCameraPosition(buildCamera(prevPosition, shouldUpdateTarget));

            if (mAnimated) {
                mMap.easeCamera(cameraUpdate);
            } else {
                mMap.moveCamera(cameraUpdate);
            }
        }
    }

    private CameraPosition buildCamera() {
        return buildCamera(null, true);
    }

    private CameraPosition buildCamera(CameraPosition previousPosition, boolean shouldUpdateTarget) {
        CameraPosition.Builder builder = new CameraPosition.Builder(previousPosition)
                .bearing(mHeading)
                .tilt(mPitch)
                .zoom(mZoomLevel);

        if (shouldUpdateTarget) {
            builder.target(ConvertUtils.toLatLng(mCenterCoordinate));
        }

        return builder.build();
    }

    private void updateUISettings() {
        if (mMap == null) {
            return;
        }
        // Gesture settings
        UiSettings uiSettings = mMap.getUiSettings();
        uiSettings.setScrollGesturesEnabled(mScrollEnabled);
        uiSettings.setTiltGesturesEnabled(mPitchEnabled);
    }

    private void setMinMaxZoomLevels() {
        if (mMap == null) {
            return;
        }

        if (mMinZoomLevel != null) {
            mMap.setMinZoomPreference(mMinZoomLevel);
        }

        if (mMaxZoomLevel != null) {
            mMap.setMaxZoomPreference(mMaxZoomLevel);
        }
    }

    private void enableLocationLayer() {
        if (mLocationEngine == null) {
            mLocationEngine = LostLocationEngine.getLocationEngine(mContext);
            mLocationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
            mLocationEngine.addLocationEngineListener(this);
            mLocationEngine.activate();
        }

        if (mLocationLayer == null) {
            mLocationLayer = new LocationLayerPlugin(mMapView, mMap, mLocationEngine);

        }

        if (mUserTrackingMode != mLocationLayer.getLocationLayerMode()) {
            mLocationLayer.setLocationLayerEnabled(mUserTrackingMode);
        }
    }

    private WritableMap makeRegionPayload(boolean isAnimated) {
        CameraPosition position = mMap.getCameraPosition();
        LatLng latLng = new LatLng(position.target.getLatitude(), position.target.getLongitude());

        WritableMap properties = new WritableNativeMap();
        properties.putDouble("zoomLevel", position.zoom);
        properties.putDouble("heading", position.bearing);
        properties.putDouble("pitch", position.tilt);
        properties.putBoolean("animated", isAnimated);

        return ConvertUtils.toPointFeature(latLng, properties);
    }
}
