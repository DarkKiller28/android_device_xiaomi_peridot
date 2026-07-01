# Vendor Mi Cam
if [ ! -f "vendor/xiaomi/peridot-miuicamera/proprietary/system/priv-app/MiuiCamera/MiuiCamera.apk" ]; then
    git clone -b aetheria-1.0 https://gitlab.com/ReversedRuler-Space/proprietary_vendor_xiaomi_peridot-miuicamera.git vendor/xiaomi/peridot-miuicamera
else
    echo "vendor/xiaomi/peridot-miuicamera already exists, skipping..."
fi
