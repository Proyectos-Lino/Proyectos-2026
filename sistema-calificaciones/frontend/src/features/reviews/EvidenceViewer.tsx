import { useState } from 'react'
import { Download, RotateCw, ZoomIn, ZoomOut } from 'lucide-react'
import type { Evidence } from '../../api/types'
import { Modal } from '../../components/Modal'

export function EvidenceViewer({ evidence, url, onClose }: { evidence: Evidence; url: string; onClose: () => void }) {
  const [zoom, setZoom] = useState(1)
  const [rotation, setRotation] = useState(0)
  const [dimensions, setDimensions] = useState({ width: 700, height: 700 })
  const baseWidth = Math.min(dimensions.width, window.innerWidth * .72, 900)
  const width = baseWidth * zoom
  const height = width * dimensions.height / dimensions.width
  return <Modal title="Evidencia del caso" description={`${evidence.filename} · ${evidence.uploadedByName ?? 'Usuario no disponible'} · ${new Date(evidence.uploadedAt).toLocaleString('es-PE')}`}
    wide onClose={onClose}><div className="modal-form">
      <div className="evidence-viewer__toolbar"><button type="button" className="button button--secondary" disabled={zoom <= .5} onClick={() => setZoom((value) => Math.max(.5, value - .25))}><ZoomOut size={17} /> Reducir</button>
        <span aria-live="polite">{Math.round(zoom * 100)} %</span>
        <button type="button" className="button button--secondary" disabled={zoom >= 3} onClick={() => setZoom((value) => Math.min(3, value + .25))}><ZoomIn size={17} /> Ampliar</button>
        <button type="button" className="button button--secondary" onClick={() => setRotation((value) => (value + 90) % 360)}><RotateCw size={17} /> Girar</button>
        <button type="button" className="text-link" onClick={() => { setZoom(1); setRotation(0) }}>Restablecer</button>
        <a className="button button--secondary" href={url} download={evidence.filename}><Download size={17} /> Descargar</a>
      </div><div className="evidence-viewer__canvas"><div style={{ position: 'relative', width: rotation % 180 ? height : width, height: rotation % 180 ? width : height }}>
        <img src={url} alt={evidence.filename} onLoad={(event) => setDimensions({ width: event.currentTarget.naturalWidth, height: event.currentTarget.naturalHeight })}
          style={{ position: 'absolute', left: '50%', top: '50%', width, height, transform: `translate(-50%, -50%) rotate(${rotation}deg)` }} /></div></div>
    </div></Modal>
}
