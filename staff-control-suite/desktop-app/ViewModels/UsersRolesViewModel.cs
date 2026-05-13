using System.Collections.ObjectModel;
using System.Text.Json;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using MaterialDesignThemes.Wpf;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;
using StaffControlSuite.Views.Dialogs;

namespace StaffControlSuite.ViewModels;

public partial class UsersRolesViewModel : ObservableObject
{
    public ObservableCollection<User> Users { get; } = new();
    public ObservableCollection<Role> Roles { get; } = new();

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasUserError))]
    private string _userErrorMessage = "";

    [ObservableProperty] private bool _isLoadingUsers;
    [ObservableProperty] private bool _isLoadingRoles;

    public bool HasUserError => !string.IsNullOrEmpty(UserErrorMessage);
    public bool CanManageUsers => App.AuthService.HasPermission("users.manage") || App.AuthService.IsOwner();
    public bool IsOwner => App.AuthService.IsOwner();

    [RelayCommand]
    public async Task LoadUsersAsync()
    {
        IsLoadingUsers = true;
        UserErrorMessage = "";
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.UsersList, null, App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                Users.Clear();
                var arr = result.ValueKind == JsonValueKind.Array ? result
                    : result.TryGetProperty("users", out var ua) ? ua : default;
                if (arr.ValueKind == JsonValueKind.Array)
                {
                    foreach (var u in arr.EnumerateArray())
                        Users.Add(ParseUser(u));
                }
            });
        }
        catch (Exception ex)
        {
            UserErrorMessage = ex.Message;
        }
        finally
        {
            IsLoadingUsers = false;
        }
    }

    [RelayCommand]
    public async Task LoadRolesAsync()
    {
        IsLoadingRoles = true;
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.RolesList, null, App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                Roles.Clear();
                var arr = result.ValueKind == JsonValueKind.Array ? result
                    : result.TryGetProperty("roles", out var ra) ? ra : default;
                if (arr.ValueKind == JsonValueKind.Array)
                {
                    foreach (var r in arr.EnumerateArray())
                        Roles.Add(ParseRole(r));
                }
            });
        }
        catch
        {
            // Silently handle
        }
        finally
        {
            IsLoadingRoles = false;
        }
    }

    [RelayCommand]
    private async Task CreateUserAsync()
    {
        var roleNames = Roles.Select(r => r.Name).ToList();
        var dialog = new UserEditDialog(null, roleNames);
        var result = await DialogHost.Show(dialog, "RootDialogHost");
        if (result is (string username, string password, string roleName))
        {
            IsLoadingUsers = true;
            try
            {
                var role = Roles.FirstOrDefault(r => r.Name == roleName);
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.UsersCreate,
                    new { username, password, roleId = role?.Id ?? 0 },
                    App.AuthService.SessionToken);
                await LoadUsersAsync();
            }
            catch (Exception ex)
            {
                UserErrorMessage = ex.Message;
            }
            finally { IsLoadingUsers = false; }
        }
    }

    [RelayCommand]
    private async Task EditUserAsync(User? user)
    {
        if (user == null) return;
        var roleNames = Roles.Select(r => r.Name).ToList();
        var dialog = new UserEditDialog(user, roleNames);
        var result = await DialogHost.Show(dialog, "RootDialogHost");
        if (result is (string username, string _, string roleName))
        {
            IsLoadingUsers = true;
            try
            {
                var role = Roles.FirstOrDefault(r => r.Name == roleName);
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.UsersUpdate,
                    new { userId = user.Id, username, roleId = role?.Id ?? 0 },
                    App.AuthService.SessionToken);
                await LoadUsersAsync();
            }
            catch (Exception ex)
            {
                UserErrorMessage = ex.Message;
            }
            finally { IsLoadingUsers = false; }
        }
    }

    [RelayCommand]
    private async Task DeleteUserAsync(User? user)
    {
        if (user == null) return;
        var confirm = await DialogHost.Show(
            new ConfirmDialog($"Delete user '{user.Username}'?", "This will permanently remove the user account."),
            "RootDialogHost");
        if (confirm is true)
        {
            IsLoadingUsers = true;
            try
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.UsersDelete,
                    new { id = user.Id },
                    App.AuthService.SessionToken);
                await LoadUsersAsync();
            }
            catch (Exception ex)
            {
                UserErrorMessage = ex.Message;
            }
            finally { IsLoadingUsers = false; }
        }
    }

    [RelayCommand]
    private async Task CreateRoleAsync()
    {
        var dialog = new RoleEditDialog(null, Permission.AllPermissions.ToList());
        var result = await DialogHost.Show(dialog, "RootDialogHost");
        if (result is Role newRole)
        {
            IsLoadingRoles = true;
            try
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.RolesCreate,
                    new { name = newRole.Name, displayName = newRole.DisplayName, permissions = newRole.Permissions },
                    App.AuthService.SessionToken);
                await LoadRolesAsync();
            }
            catch { }
            finally { IsLoadingRoles = false; }
        }
    }

    [RelayCommand]
    private async Task EditRoleAsync(Role? role)
    {
        if (role == null) return;
        var dialog = new RoleEditDialog(role, Permission.AllPermissions.ToList());
        var result = await DialogHost.Show(dialog, "RootDialogHost");
        if (result is Role updated)
        {
            IsLoadingRoles = true;
            try
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.RolesUpdate,
                    new { roleId = role.Id, displayName = updated.DisplayName, permissions = updated.Permissions },
                    App.AuthService.SessionToken);
                await LoadRolesAsync();
            }
            catch { }
            finally { IsLoadingRoles = false; }
        }
    }

    [RelayCommand]
    private async Task DeleteRoleAsync(Role? role)
    {
        if (role == null || role.IsSystemRole) return;
        var confirm = await DialogHost.Show(
            new ConfirmDialog($"Delete role '{role.DisplayName}'?", "Users with this role will need reassignment."),
            "RootDialogHost");
        if (confirm is true)
        {
            IsLoadingRoles = true;
            try
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.RolesDelete,
                    new { id = role.Id },
                    App.AuthService.SessionToken);
                await LoadRolesAsync();
            }
            catch { }
            finally { IsLoadingRoles = false; }
        }
    }

    private static User ParseUser(JsonElement u)
    {
        var user = new User
        {
            Id = u.TryGetProperty("id", out var id) ? id.GetInt64() : 0,
            Username = u.TryGetProperty("username", out var un) ? un.GetString() ?? "" : "",
            RoleId = u.TryGetProperty("roleId", out var rid) ? rid.GetInt64() : 0,
            RoleName = u.TryGetProperty("roleName", out var rn) ? rn.GetString() ?? "" : "",
            IsActive = u.TryGetProperty("isActive", out var ia) && ia.GetBoolean(),
            CreatedAt = u.TryGetProperty("createdAt", out var ca) ? ca.GetInt64() : 0L
        };
        if (u.TryGetProperty("permissions", out var perms))
        {
            user.Permissions = perms.EnumerateArray()
                .Select(p => p.GetString() ?? "")
                .Where(p => !string.IsNullOrEmpty(p))
                .ToList();
        }
        return user;
    }

    private static Role ParseRole(JsonElement r)
    {
        var role = new Role
        {
            Id = r.TryGetProperty("id", out var id) ? id.GetInt64() : 0,
            Name = r.TryGetProperty("name", out var n) ? n.GetString() ?? "" : "",
            DisplayName = r.TryGetProperty("displayName", out var dn) ? dn.GetString() ?? "" : "",
            IsSystemRole = r.TryGetProperty("isSystemRole", out var isr) && isr.GetBoolean(),
            CreatedAt = r.TryGetProperty("createdAt", out var ca) ? ca.GetInt64() : 0L
        };
        if (r.TryGetProperty("permissions", out var perms))
        {
            role.Permissions = perms.EnumerateArray()
                .Select(p => p.GetString() ?? "")
                .Where(p => !string.IsNullOrEmpty(p))
                .ToList();
        }
        return role;
    }
}
