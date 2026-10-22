import type { ComponentProps } from 'react'

/** Reveal folded fields before native form validation tries to focus them. */
export function OptionalSection(props: ComponentProps<'details'>) {
  return (
    <details
      {...props}
      onInvalidCapture={(event) => {
        let section = event.target instanceof HTMLElement ? event.target.closest('details') : null
        while (section) {
          section.open = true
          section = section.parentElement?.closest('details') ?? null
        }
        props.onInvalidCapture?.(event)
      }}
    />
  )
}
