﻿using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Threading;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.System;
using Windows.UI.Xaml.Media;
using org_comroid_status_api;

// Die Elementvorlage "Leere Seite" wird unter https://go.microsoft.com/fwlink/?LinkId=402352&clcid=0x407 dokumentiert.

namespace status_app
{
    /// <summary>
    /// Eine leere Seite, die eigenständig verwendet oder zu der innerhalb eines Rahmens navigiert werden kann.
    /// </summary>
    public sealed partial class MainPage : Page
    {
        public T FindControl<T>(Type targetType, string ControlName) where T : FrameworkElement
        {
            return FindControl<T>(this, targetType, ControlName);
        }

        public T FindControl<T>(UIElement parent, Type targetType, string ControlName) where T : FrameworkElement
        {
            if (parent == null) return null;

            if (parent.GetType() == targetType && ((T) parent).Name == ControlName)
            {
                return (T) parent;
            }

            T result = null;
            int count = VisualTreeHelper.GetChildrenCount(parent);
            for (int i = 0; i < count; i++)
            {
                UIElement child = (UIElement) VisualTreeHelper.GetChild(parent, i);

                if (FindControl<T>(child, targetType, ControlName) != null)
                {
                    result = FindControl<T>(child, targetType, ControlName);
                    break;
                }
            }

            return result;
        }

        public static readonly Uri Homepage = new Uri("https://status.comroid.org");
        internal static readonly StatusConnection Connection = new StatusConnection();

        public MainPage()
        {
            this.InitializeComponent();
        }

        private async void ReloadPage(object sender, RoutedEventArgs e)
        {
            Debug.WriteLine("Initiating Page reload");

            List<Service> services = await Connection.RefreshServiceCache();

            foreach (Service service in services)
            {
                ServiceBox existing = ComputeServiceBox(service);
                existing.UpdateDisplay(service);
            }

            Debug.WriteLine(
                $"Reload complete with {services.Count} services; Stacker has {Stacker.Children.Count} children");
        }

        internal StackPanel Stacker => FindControl<StackPanel>(typeof(StackPanel), "ServicePanel");

        private ServiceBox ComputeServiceBox(Service service)
        {
            return Stacker.Children
                       .Select(each => each as ServiceBox)
                       .Where(each => each != null)
                       .FirstOrDefault(box => box.Name.Equals($"status_{service.Name.Replace('-','_')}"))
                   ?? new ServiceBox(this, service);
        }

        private void InitializeServiceList(object sender, RoutedEventArgs e)
        {
            ReloadPage(sender, e);
        }

        private async void OpenInBrowser(object sender, RoutedEventArgs e)
        {
            await Launcher.LaunchUriAsync(Homepage);
        }

        internal sealed class ServiceBox : StackPanel
        {
            private readonly TextBox _displayName;
            private readonly TextBox _statusText;

            internal ServiceBox(MainPage mainPage, Service service)
            {
                Name = $"status_{service.Name.Replace('-','_')}";
                Visibility = Visibility.Visible;
                Margin = new Thickness(25, 50, 25, 50);
                Background = mainPage.Resources["AppBarItemPointerOverBackgroundThemeBrush"] as Brush;
                HorizontalAlignment = HorizontalAlignment.Stretch;
                VerticalAlignment = VerticalAlignment.Center;

                this._displayName = new TextBox()
                {
                    Text = service.DisplayName,
                    Style = mainPage.Resources["TitleTextBlockStyle"] as Style,
                    FontSize = 25,
                    HorizontalAlignment = HorizontalAlignment.Stretch,
                    VerticalAlignment = VerticalAlignment.Stretch,
                    TextAlignment = TextAlignment.Center
                };
                this._statusText = new TextBox()
                {
                    Text = ServiceStatus.Unknown.Display,
                    Style = mainPage.Resources["BodyTextBlockStyle"] as Style,
                    FontSize = 18,
                    HorizontalAlignment = HorizontalAlignment.Stretch,
                    VerticalAlignment = VerticalAlignment.Stretch,
                    TextAlignment = TextAlignment.Center
                };

                Children.Add(_displayName);
                Children.Add(_statusText);
                mainPage.Stacker.Children.Add(this);

                UpdateDisplay(service);
            }

            public void UpdateDisplay(Service service)
            {
                if (!Name.Equals($"status_{service.Name.Replace('-','_')}"))
                    throw new ArgumentException("Service ID mismatch");
                _statusText.Text = service.GetStatus().Display;
            }
        }
    }
}