"use client"

import "leaflet/dist/leaflet.css"

import { useEffect, useMemo, useRef } from "react"
import L from "leaflet"
import { MapContainer, Marker, TileLayer, useMap } from "react-leaflet"

interface NodeMapPickerProps {
  latitude: number
  longitude: number
  onChange: (position: { latitude: number; longitude: number }) => void
}

// a plain colored pin rendered as a divIcon — avoids depending on Leaflet's default marker
// image paths, which don't resolve correctly through Next.js's bundler without extra config
const pinIcon = L.divIcon({
  className: "",
  html: '<div class="size-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white bg-primary shadow-md"></div>',
  iconSize: [16, 16],
  iconAnchor: [8, 8],
})

// registers the map's click listener exactly once (via a stable [map] effect dependency) and
// reads the latest onChange through a ref, instead of react-leaflet's useMapEvents — which
// re-subscribes on every render because the inline onChange prop from CreateNodeDialog gets a
// new identity each time, and that resubscribe-per-click-per-render churn was hanging the tab
function ClickHandler({ onChange }: Pick<NodeMapPickerProps, "onChange">) {
  const map = useMap()
  const onChangeRef = useRef(onChange)

  // ref writes belong in an effect, not render (react-hooks/refs) — this still runs after
  // every render where onChange's identity changed, keeping the ref current for handleClick
  useEffect(() => {
    onChangeRef.current = onChange
  }, [onChange])

  useEffect(() => {
    function handleClick(event: L.LeafletMouseEvent) {
      onChangeRef.current({ latitude: event.latlng.lat, longitude: event.latlng.lng })
    }

    map.on("click", handleClick)
    return () => {
      map.off("click", handleClick)
    }
  }, [map])

  return null
}

// click-to-place-a-pin GPS picker for node registration. Leaflet + OpenStreetMap tiles need no
// API key/billing, unlike the mobile app's Google Maps usage (project-specification.md section
// 4.3) — a separate, native-Android concern the web app doesn't need to match. Must be loaded
// via next/dynamic with ssr:false (see create-node-dialog.tsx) since Leaflet touches `window`.
export function NodeMapPicker({ latitude, longitude, onChange }: NodeMapPickerProps) {
  // only used as the map's initial center on mount; moving the pin doesn't recenter the map
  const initialCenter = useMemo<[number, number]>(() => [latitude, longitude], []) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="min-h-72 w-full flex-1 overflow-hidden rounded-lg border border-input">
      <MapContainer
        center={initialCenter}
        zoom={12}
        className="h-full w-full"
        scrollWheelZoom={false}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <Marker position={[latitude, longitude]} icon={pinIcon} />
        <ClickHandler onChange={onChange} />
      </MapContainer>
    </div>
  )
}
