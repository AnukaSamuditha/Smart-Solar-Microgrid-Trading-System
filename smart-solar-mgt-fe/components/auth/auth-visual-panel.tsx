import React from 'react'

const PANEL_GRADIENT =
  'linear-gradient(145deg, #F4FFAE 0%, #E9FC77 22%, #DCF64A 44%, #CBEA26 64%, #B7D617 82%, #A6C40E 100%)'

const DOT_PATTERN =
  'radial-gradient(rgba(255, 255, 255, 0.7) 1.4px, transparent 1.4px)'

const DOT_MASK =
  'linear-gradient(145deg, rgba(0, 0, 0, 0.3) 0%, rgba(0, 0, 0, 0.7) 35%, rgba(0, 0, 0, 1) 70%)'

export function AuthVisualPanel() {
  return (
    <aside
      className="relative isolate flex flex-col overflow-hidden"
      style={{ backgroundImage: PANEL_GRADIENT }}
    >
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          backgroundImage: DOT_PATTERN,
          backgroundSize: '18px 18px',
          maskImage: DOT_MASK,
          WebkitMaskImage: DOT_MASK,
        }}
        aria-hidden="true"
      />

      <div className="relative flex flex-1 flex-col justify-between gap-16 p-8 sm:p-10 lg:p-14">
        <header className="flex items-center justify-between gap-4">
          <a
          href="#"
          className="flex items-center gap-3 rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-black/30 focus-visible:ring-offset-2 focus-visible:ring-offset-transparent"
        >
          {/* Wattex icon */}
          <span className="flex h-11 w-11 items-center justify-center">
            <svg
              viewBox="0 0 32 32"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              className="h-10 w-10 text-[#10130D]"
              aria-hidden="true"
            >
              <path
                d="M17.8 3.5L7.5 17.1H15L13.8 28.5L24.5 14.8H17L17.8 3.5Z"
                fill="currentColor"
              />
            </svg>
          </span>
        
          {/* Wordmark */}
          <span className="font-heading text-xl font-bold tracking-[-0.03em] text-[#10130D]">
            Wattex
          </span>
        </a>

          {/*<span className="hidden items-center gap-2 rounded-full border border-ink/15 bg-white/25 px-3 py-1.5 text-[11px] font-medium tracking-wide text-ink/70 backdrop-blur-sm sm:inline-flex">
            <span className="h-1.5 w-1.5 rounded-full bg-ink/70" />
            Grid live
          </span>*/}
        </header>

        <div className="max-w-md">
          <h1 className="font-heading text-3xl font-semibold leading-[1.1] tracking-tight text-ink sm:text-4xl xl:text-[2.75rem]">
            Your solar.
            <br />
            <span className="text-ink/55">Everyone&rsquo;s grid.</span>
          </h1>
          <p className="mt-4 max-w-sm text-sm leading-relaxed text-ink/70">
            Buy and sell renewable energy directly with your neighbors, settled
            in real time across your local microgrid.
          </p>

          <dl className="mt-8 grid grid-cols-3 border-t border-ink/15 pt-6">
            <div className="pr-4">
              <dt className="text-[11px] font-medium uppercase tracking-[0.12em] text-ink/55">
                Trading
              </dt>
              <dd className="mt-1 font-heading text-sm font-semibold tracking-tight text-ink sm:text-base">
                Peer-to-peer
              </dd>
            </div>
            <div className="border-l border-ink/15 pl-4">
              <dt className="text-[11px] font-medium uppercase tracking-[0.12em] text-ink/55">
                Settlement
              </dt>
              <dd className="mt-1 font-heading text-sm font-semibold tracking-tight text-ink sm:text-base">
                Real-time
              </dd>
            </div>
            <div className="border-l border-ink/15 pl-4">
              <dt className="text-[11px] font-medium uppercase tracking-[0.12em] text-ink/55">
                Pricing
              </dt>
              <dd className="mt-1 font-heading text-sm font-semibold tracking-tight text-ink sm:text-base">
                Fully transparent
              </dd>
            </div>
          </dl>
        </div>
      </div>
    </aside>
  )
}
