import { Beaker, LockKeyhole, Mail } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, ErrorNotice } from '../../../shared/components/ui';
import { getErrorMessage } from '../../../shared/lib/errors';
import { useAuth } from '../hooks/useAuth';
import { useOrganization } from '../../organization/hooks/useOrganization';

export function LoginPage() {
  const { login } = useAuth();
  const { organization } = useOrganization();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      await login(email, password);
      navigate('/');
    } catch (reason) {
      setError(getErrorMessage(reason, 'No fue posible iniciar sesión.'));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="grid min-h-screen bg-canvas lg:grid-cols-[1.1fr_0.9fr]">
      <section
        className="relative hidden overflow-hidden p-12 text-white lg:flex lg:flex-col lg:justify-between"
        style={{ backgroundColor: organization?.secondaryColor }}
      >
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,_rgba(45,212,191,0.22),_transparent_36%),radial-gradient(circle_at_bottom_left,_rgba(56,189,248,0.14),_transparent_34%)]" />
        <div className="relative flex items-center gap-3">
          <span
            className="grid size-11 place-items-center overflow-hidden rounded-2xl shadow-lg"
            style={{ backgroundColor: organization?.primaryColor }}
          >
            {organization?.logoDataUrl ? (
              <img alt="" className="size-full object-contain p-1" src={organization.logoDataUrl} />
            ) : (
              <Beaker size={23} />
            )}
          </span>
          <span className="text-lg font-bold tracking-tight">{organization?.commercialName}</span>
        </div>
        <div className="relative">
          <p className="max-w-xl text-5xl font-bold leading-tight tracking-tight">
            Trazabilidad confiable para cada muestra.
          </p>
          <p className="mt-6 max-w-lg text-base leading-7 text-slate-300">
            Una plataforma general para registrar, ejecutar y validar los análisis de tu
            laboratorio.
          </p>
        </div>
        <p className="relative text-sm text-slate-400">Software de gestión de laboratorio</p>
      </section>

      <section className="flex items-center justify-center px-5 py-10 sm:p-10">
        <form
          className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-7 shadow-[0_20px_50px_-28px_rgba(15,23,42,0.34)] sm:p-9"
          onSubmit={handleSubmit}
        >
          <div className="mb-8 lg:hidden">
            <div className="flex items-center gap-2 font-bold text-lab-950">
              <Beaker className="text-lab-600" size={21} />
              {organization?.commercialName}
            </div>
          </div>
          <div>
            <p className="text-3xl font-bold tracking-tight text-slate-950">Bienvenido</p>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              Ingresa con tus credenciales de trabajo.
            </p>
          </div>

          <div className="mt-6">
            <ErrorNotice message={error} />
          </div>

          <div className="mt-6 space-y-5">
            <div className="space-y-2">
              <label htmlFor="email">Correo electrónico</label>
              <div className="relative">
                <Mail
                  aria-hidden="true"
                  className="pointer-events-none absolute left-3.5 top-3 text-slate-400"
                  size={17}
                />
                <input
                  autoComplete="email"
                  className="w-full pl-10"
                  id="email"
                  onChange={(event) => setEmail(event.target.value)}
                  required
                  type="email"
                  value={email}
                />
              </div>
            </div>

            <div className="space-y-2">
              <label htmlFor="password">Contraseña</label>
              <div className="relative">
                <LockKeyhole
                  aria-hidden="true"
                  className="pointer-events-none absolute left-3.5 top-3 text-slate-400"
                  size={17}
                />
                <input
                  autoComplete="current-password"
                  className="w-full pl-10"
                  id="password"
                  onChange={(event) => setPassword(event.target.value)}
                  required
                  type="password"
                  value={password}
                />
              </div>
            </div>
          </div>

          <Button className="mt-7 w-full" disabled={isSubmitting} type="submit">
            {isSubmitting ? 'Ingresando…' : 'Ingresar al sistema'}
          </Button>
          <p className="mt-5 text-center text-xs leading-5 text-slate-400">
            El acceso está protegido y se registra por usuario.
          </p>
        </form>
      </section>
    </div>
  );
}
