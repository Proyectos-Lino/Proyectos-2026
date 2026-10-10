import { Download, ExternalLink } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Button, Modal, ModalFooter } from '../../../shared/components/ui';
import { getReportPdf } from '../api/reports-api';

interface ReportPdfModalProps {
  code: string;
  isOpen: boolean;
  reportId: string;
  onClose: () => void;
}

export function ReportPdfModal({ code, isOpen, reportId, onClose }: ReportPdfModalProps) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      return undefined;
    }
    let active = true;
    let objectUrl: string | null = null;
    void getReportPdf(reportId).then((blob) => {
      if (active) {
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      }
    });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      setUrl(null);
    };
  }, [isOpen, reportId]);

  function download() {
    if (!url) return;
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${code}.pdf`;
    anchor.click();
  }

  return (
    <Modal
      description="Documento PDF conservado para esta versión del informe."
      isOpen={isOpen}
      onClose={onClose}
      panelClassName="max-w-6xl"
      title={`Vista previa · ${code}`}
    >
      <div className="h-[65vh] overflow-hidden rounded-xl border border-slate-200 bg-slate-100">
        {url ? (
          <iframe className="h-full w-full" src={url} title={`Informe ${code}`} />
        ) : (
          <p className="grid h-full place-items-center text-sm text-slate-500">Cargando PDF…</p>
        )}
      </div>
      <ModalFooter>
        <Button onClick={onClose} variant="secondary">
          Cerrar
        </Button>
        <Button
          disabled={!url}
          onClick={() => url && window.open(url, '_blank')}
          variant="secondary"
        >
          <ExternalLink size={16} />
          Abrir aparte
        </Button>
        <Button disabled={!url} onClick={download}>
          <Download size={16} />
          Descargar PDF
        </Button>
      </ModalFooter>
    </Modal>
  );
}
